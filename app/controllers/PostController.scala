package controllers

import javax.inject.Inject
import javax.inject.Singleton

import dao.post.PostDAO
import models.post.Post
import models.post.Posts
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.Controller

/**
  * Marco Ebert 20.05.16
  */
@Singleton
final class PostController @Inject()(dao: PostDAO) extends Controller {

  /**
    * Indexes a post.
    *
    * @return If the post has been indexed.
    */
  def index = Action.async(parse.json[Post]) { request =>
    val post = request.body
    dao index post map { indexed =>
      Ok(Json.obj("indexed" -> indexed))
    }
  }

  /**
    * Indexes multiple posts.
    *
    * @return If the posts have been indexed.
    */
  def bulk = Action.async(parse.json[Posts]) { request =>
    val posts = request.body.posts
    dao index posts map { indexed =>
      Ok(Json.obj("indexed" -> indexed))
    }
  }

  /**
    * Deletes a post by ID.
    *
    * @param id Post ID.
    * @return If a post has been found and deleted.
    */
  def delete(id: Int) = Action.async {
    dao delete id map { deleted =>
      Ok(Json.obj("deleted" -> deleted))
    }
  }

}
