import play.api._
import scala.concurrent.Await
import scala.concurrent.duration._

object Global extends GlobalSettings {

  override def onStart(app: Application) {
    val initFuture = controllers.Application.init
    Await.ready(initFuture, 5 seconds)
    Logger.info("Global application has started")
  }

  override def onStop(app: Application) {
    Logger.info("Global application shutdown...")
  }

}
