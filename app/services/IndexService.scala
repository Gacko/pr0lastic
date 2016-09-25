package services

import javax.inject.Inject
import javax.inject.Singleton

import models.Index
import org.elasticsearch.client.Client
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import util.Helpers._

import scala.collection.JavaConversions._
import scala.concurrent.Future

/**
  * Marco Ebert 21.05.16
  */
@Singleton
final class IndexService @Inject()(client: Client, index: Index) {

  /**
    * Creates an index.
    *
    * @return Index name.
    */
  private def create: Future[String] = {
    val name = index.name
    Logger.info(s"IndexService::create: Creating index '$name'.")

    val request = client.admin().indices().prepareCreate(name)
    request.setSettings(index.settings)

    for ((name, mapping) <- index.mappings) {
      request.addMapping(name, mapping)
    }

    val response = request.execute()
    response.map { response =>
      Logger.info(s"IndexService::create: Created index '$name'.")
      name
    }
  }

  /**
    * Deletes an index by name.
    *
    * @param name Index name.
    * @return Deletion status.
    */
  private def delete(name: String): Future[Boolean] = {
    Logger.info(s"IndexService::delete: Deleting index '$name'.")
    val request = client.admin().indices().prepareDelete(name)
    val response = request.execute()
    response.map { response =>
      Logger.info(s"IndexService::delete: Deleted index '$name'.")
      response.isAcknowledged
    }
  }

  /**
    * Maps index names by alias.
    *
    * @return Index names by alias.
    */
  private def aliases: Future[Map[String, String]] = {
    val request = client.admin().indices().prepareGetAliases()
    val response = request.execute()
    response.map { response =>
      val pairs = for {
        cursor <- response.getAliases
        value <- cursor.value
      } yield {
        value.alias -> cursor.key
      }

      pairs.toMap
    }
  }

  /**
    * Adds an alias to an index. Optionally removes the alias from another index.
    *
    * @param name   Index to set alias to.
    * @param alias  Alias to set for index.
    * @param remove Index to remove alias from.
    * @return Alias status.
    */
  private def setAlias(name: String, alias: String, remove: Option[String]): Future[Boolean] = {
    Logger.info(s"IndexService::setAlias: Adding alias '$alias' to index '$name'.")
    val request = client.admin().indices().prepareAliases()

    remove.foreach(request.removeAlias(_, alias))
    request.addAlias(name, alias)

    val response = request.execute()
    response.map { response =>
      Logger.info(s"IndexService::setAlias: Added alias '$alias' to index '$name'.")
      response.isAcknowledged
    }
  }

  /**
    * Sets the read alias to the write aliased index if they are not equal.
    * Sets the backup alias to the previously read aliased index.
    * Deletes the previously backup aliased index afterwards.
    *
    * OR
    *
    * Creates a write aliased index if none exists.
    *
    * @return
    */
  def switch: Future[Boolean] = {
    Logger.info(s"IndexService::switch: Switching indices.")
    aliases.flatMap { aliases =>
      val read = aliases.get(index.read)
      val write = aliases.get(index.write)

      val switch = write match {
        case Some(w) if read != write =>
          val backup = aliases.get(index.backup)
          for {
            r <- read if read != backup
            alias <- setAlias(r, index.backup, backup)
            b <- backup
          } delete(b)

          setAlias(w, index.read, read)
        case _ =>
          for {
            name <- create
            alias <- setAlias(name, index.write, write)
          } yield alias
      }

      switch.foreach(_ => Logger.info(s"IndexService::switch: Switched indices."))
      switch
    }
  }

  /**
    * Sets the write alias to the read aliased index if they are not equal.
    * Deletes the previously write aliased index afterwards.
    *
    * OR
    *
    * Sets the read alias to the backup aliased index if read and write are equal.
    *
    * @return
    */
  def rollback: Future[Boolean] = {
    Logger.info(s"IndexService::rollback: Rolling back indices.")
    aliases.flatMap { aliases =>
      val read = aliases.get(index.read)
      val write = aliases.get(index.write)

      val rollback = read match {
        case Some(r) if read != write =>
          val alias = setAlias(r, index.write, write)
          alias.foreach(_ => write.foreach(delete))
          alias
        case _ =>
          val backup = aliases.get(index.backup)
          backup match {
            case Some(b) if read != backup => setAlias(b, index.read, read)
            case _ => Future.successful(false)
          }
      }

      rollback.foreach(_ => Logger.info(s"IndexService::rollback: Rolled back indices."))
      rollback
    }
  }

}
