package io.hydrosphere.serving.manager.service.envoy

import akka.actor.{ActorRef, ActorSystem, Props}
import envoy.api.v2.{DiscoveryRequest, DiscoveryResponse}
import io.grpc.stub.StreamObserver
import io.hydrosphere.serving.manager.ManagerConfiguration
import io.hydrosphere.serving.manager.service._
import io.hydrosphere.serving.manager.service.application.ApplicationManagementService
import io.hydrosphere.serving.manager.service.clouddriver.CloudDriverService
import io.hydrosphere.serving.manager.service.envoy.xds._
import io.hydrosphere.serving.manager.service.service.ServiceManagementService
import org.apache.logging.log4j.scala.Logging

import scala.concurrent.ExecutionContext

trait EnvoyGRPCDiscoveryService {
  def subscribe(discoveryRequest: DiscoveryRequest, responseObserver: StreamObserver[DiscoveryResponse]): Unit

  def unsubscribe(responseObserver: StreamObserver[DiscoveryResponse]): Unit
}

class EnvoyGRPCDiscoveryServiceImpl(
  serviceManagementService: ServiceManagementService,
  applicationManagementService: ApplicationManagementService,
  cloudDriverService: CloudDriverService,
  managerConfiguration: ManagerConfiguration
)(
  implicit val ex: ExecutionContext,
  actorSystem: ActorSystem
) extends EnvoyGRPCDiscoveryService with Logging {

  private val clusterDSActor: ActorRef = actorSystem.actorOf(Props[ClusterDSActor])
  //TODO use managerConfiguration for endpoint configuration
  private val endpointDSActor: ActorRef = actorSystem.actorOf(Props(classOf[EndpointDSActor],
    false,
    "mainapplication",
    9091
  ))
  private val listenerDSActor: ActorRef = actorSystem.actorOf(Props[ListenerDSActor])
  private val routeDSActor: ActorRef = actorSystem.actorOf(Props[RouteDSActor])
  private val applicationDSActor: ActorRef = actorSystem.actorOf(Props[ApplicationDSActor])

  private val xdsManagementActor: ActorRef = actorSystem.actorOf(Props(classOf[XDSManagementActor],
    serviceManagementService,
    applicationManagementService,
    cloudDriverService,
    clusterDSActor,
    endpointDSActor,
    listenerDSActor,
    routeDSActor,
    applicationDSActor,
    ex
  ))

  override def subscribe(discoveryRequest: DiscoveryRequest, responseObserver: StreamObserver[DiscoveryResponse]): Unit =
    discoveryRequest.node.foreach(_ => {
      xdsManagementActor ! SubscribeMsg(
        discoveryRequest = discoveryRequest,
        responseObserver = responseObserver
      )
    })

  override def unsubscribe(responseObserver: StreamObserver[DiscoveryResponse]): Unit = {
    val msg = UnsubscribeMsg(responseObserver)
    xdsManagementActor ! msg
  }
}