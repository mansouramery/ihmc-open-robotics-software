package us.ihmc.humanoidRobotics.communication.packets.sensing;

import java.util.Random;

import us.ihmc.communication.packets.Packet;

public class LocalizationPacket extends Packet<LocalizationPacket>
{
   public boolean reset;
   public boolean toggle;

   public LocalizationPacket()
   {
      // Empty constructor for deserialization
   }

   public LocalizationPacket(boolean reset, boolean toggle)
   {
      this.reset = reset;
      this.toggle = toggle;
   }

   public boolean getReset()
   {
      return reset;
   }

   public boolean getToggle()
   {
      return toggle;
   }

   public boolean equals(Object other)
   {
      if (other instanceof LocalizationPacket)
      {
         LocalizationPacket otherLocalizationPacket = (LocalizationPacket) other;

         return epsilonEquals(otherLocalizationPacket, 0);
      }
      else
      {
         return false;
      }
   }

   @Override
   public boolean epsilonEquals(LocalizationPacket other, double epsilon)
   {
      return (this.reset == other.reset) && (this.toggle == other.toggle);
   }

   public LocalizationPacket(Random random)
   {
      this(random.nextBoolean(), random.nextBoolean());

   }
}
