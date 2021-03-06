package us.ihmc.humanoidRobotics.communication.packets.behaviors;

import java.util.Random;

import us.ihmc.communication.packets.Packet;
import us.ihmc.robotics.geometry.RigidBodyTransform;

public class DrillPacket extends Packet<DrillPacket>
{
   public RigidBodyTransform drillTransform;
   
   public DrillPacket(Random random)
   {
      drillTransform = RigidBodyTransform.generateRandomTransform(random);
   }

   public DrillPacket()
   {
      
   }
   
   public DrillPacket(RigidBodyTransform drillTransform)
   {
      this.drillTransform = drillTransform;
   }

   @Override
   public boolean epsilonEquals(DrillPacket other, double epsilon)
   {
      return drillTransform.epsilonEquals(other.getDrillTransform(), epsilon);
   }
   
   public RigidBodyTransform getDrillTransform()
   {
      return drillTransform;
   }
}
