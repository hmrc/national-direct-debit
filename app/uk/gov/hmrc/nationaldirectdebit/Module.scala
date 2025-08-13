package uk.gov.hmrc.nationaldirectdebit

import com.google.inject.AbstractModule
import play.api.{Configuration, Environment}
import uk.gov.hmrc.nationaldirectdebit.actions.{AuthAction, DefaultAuthAction}
import uk.gov.hmrc.nationaldirectdebit.config.AppConfig

import java.time.Clock

class Module extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[AuthAction]).to(classOf[DefaultAuthAction]).asEagerSingleton()
    bind(classOf[AppConfig]).asEagerSingleton()
  }
}