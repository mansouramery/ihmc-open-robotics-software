package us.ihmc.ihmcPerception.depthData;

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;

import javax.vecmath.Point3d;

import us.ihmc.communication.net.PacketConsumer;
import us.ihmc.communication.packetCommunicator.PacketCommunicator;
import us.ihmc.communication.packets.PacketDestination;
import us.ihmc.humanoidRobotics.communication.packets.sensing.DepthDataStateCommand;
import us.ihmc.humanoidRobotics.communication.packets.sensing.PointCloudWorldPacket;
import us.ihmc.robotics.dataStructures.TimestampedPoint;
import us.ihmc.tools.io.printing.PrintTools;
import us.ihmc.tools.thread.ThreadTools;

public class PointCloudWorldPacketGenerator implements Runnable
{
   private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(ThreadTools
         .getNamedThreadFactory("PointCloudWorldPacketGenerator"));

   private final ReadLock readLock;
   private final DepthDataStore depthDataFilter;
   private final PacketCommunicator packetCommunicator;
   private PacketDestination packetDestination = PacketDestination.BROADCAST;
   private ScheduledFuture<?> scheduled = null;

   public PointCloudWorldPacketGenerator(PacketCommunicator sensorSuitePacketCommunicator, ReadLock readLock, DepthDataStore depthDataFilter)
   {
      this.packetCommunicator = sensorSuitePacketCommunicator;
      this.readLock = readLock;
      this.depthDataFilter = depthDataFilter;
      
      packetCommunicator.attachListener(DepthDataStateCommand.class, new PacketConsumer<DepthDataStateCommand>()
      {
         @Override
         public void receivedPacket(DepthDataStateCommand depthDataStateCommand)
         {
            if (depthDataStateCommand.getLidarState() == DepthDataStateCommand.LidarState.ENABLE)
            {
               if (packetDestination != PacketDestination.BROADCAST)
               {
                  PrintTools.info("Mutlisense scan destination = " + PacketDestination.BROADCAST.name());
                  packetDestination = PacketDestination.BROADCAST;
               }
            }
            else if (depthDataStateCommand.getLidarState() == DepthDataStateCommand.LidarState.ENABLE_BEHAVIOR_ONLY)
            {
               if (packetDestination != PacketDestination.BEHAVIOR_MODULE)
               {
                  PrintTools.info("Mutlisense scan destination = " + PacketDestination.BEHAVIOR_MODULE.name());
                  packetDestination = PacketDestination.BEHAVIOR_MODULE;
               }
            }
         }
      });
   }

   PointCloudWorldPacketGenerator(DepthDataStore depthDataFilter)
   {
      readLock = new ReentrantReadWriteLock().readLock();
      this.depthDataFilter = depthDataFilter;
      this.packetCommunicator = null;
   }

   public PointCloudWorldPacket getPointCloudWorldPacket()
   {
      PointCloudWorldPacket packet = new PointCloudWorldPacket();

      readLock.lock();
      ArrayList<Point3d> groundPoints = new ArrayList<>();
      ArrayList<TimestampedPoint> nearScanTimestampedPoints = depthDataFilter.getNearScan().getPointsCopy();
      packet.defaultGroundHeight = (float) depthDataFilter.getQuadTree().getDefaultHeightWhenNoPoints();
      depthDataFilter.getQuadTree().getCellAverageStoredPoints(groundPoints);
      readLock.unlock();

      packet.setGroundQuadTreeSupport(groundPoints.toArray(new Point3d[groundPoints.size()]));

      ArrayList<Point3d> nearScanPoints = new ArrayList<>();
      for (TimestampedPoint point : nearScanTimestampedPoints)
      {
         nearScanPoints.add(new Point3d(point.x, point.y, point.z));
      }
      packet.setDecayingWorldScan(nearScanPoints.toArray(new Point3d[nearScanPoints.size()]));
      packet.timestamp = System.nanoTime();
      return packet;
   }

   public void start()
   {
      if (scheduled == null)
      {
         scheduled = executorService.scheduleAtFixedRate(this, 0, 1, TimeUnit.SECONDS);
      }

   }

   public void stop()
   {
      scheduled.cancel(true);
      executorService.shutdown();
   }

   @Override
   public void run()
   {
      try
      {
         PointCloudWorldPacket pointCloudWorldPacket = getPointCloudWorldPacket();
         pointCloudWorldPacket.setDestination(packetDestination);
         packetCommunicator.send(pointCloudWorldPacket);
      }
      catch(Exception e)
      {
         e.printStackTrace();
         throw new RuntimeException(e);
      }
   }
}
