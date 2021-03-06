package us.ihmc.acsell.network;

import java.io.IOException;
import java.net.URI;

import us.ihmc.SdfLoader.SDFFullRobotModel;
import us.ihmc.communication.net.ObjectCommunicator;
import us.ihmc.communication.packetCommunicator.PacketCommunicator;
import us.ihmc.communication.util.NetworkPorts;
import us.ihmc.darpaRoboticsChallenge.sensors.DRCSensorSuiteManager;
import us.ihmc.humanoidRobotics.kryo.IHMCCommunicationKryoNetClassList;

public class AcsellSensorSuiteManager implements DRCSensorSuiteManager
{
   private final PacketCommunicator sensorSuitePacketCommunicator = PacketCommunicator.createIntraprocessPacketCommunicator(NetworkPorts.SENSOR_MANAGER,
         new IHMCCommunicationKryoNetClassList());
   
   public AcsellSensorSuiteManager(SDFFullRobotModel sdfFullRobotModel, boolean useSimulatedSensors)
   {
   }
   
   @Override
   public void initializeSimulatedSensors(ObjectCommunicator packetCommunicator)
   {
   }

   @Override
   public void initializePhysicalSensors(URI sensorURI)
   {
   }

   @Override
   public void connect() throws IOException
   {
      sensorSuitePacketCommunicator.connect();
   }


}
