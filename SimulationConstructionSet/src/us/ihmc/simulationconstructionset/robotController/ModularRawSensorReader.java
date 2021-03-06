package us.ihmc.simulationconstructionset.robotController;

import java.util.ArrayList;
import java.util.List;

import us.ihmc.robotics.dataStructures.registry.YoVariableRegistry;

public class ModularRawSensorReader implements RawSensorReader
{
   private final ArrayList<RawSensorReader> rawSensorReaders = new ArrayList<RawSensorReader>();
   private final String description;
   private final YoVariableRegistry registry;

   public ModularRawSensorReader(String name, String description, RawSensorReader rawSensorReader)
   {
      this(name, description);
      addRawSensorReader(rawSensorReader);
   }

   public ModularRawSensorReader(String name, String description, RawSensorReader[] rawSensorReaders)
   {
      this(name, description);

      for (RawSensorReader rawSensorReader : rawSensorReaders)
      {
         addRawSensorReader(rawSensorReader);
      }
   }

   public ModularRawSensorReader(String name, String description, List<RawSensorReader> rawSensorReaders)
   {
      this(name, description);

      for (RawSensorReader rawSensorReader : rawSensorReaders)
      {
         addRawSensorReader(rawSensorReader);
      }
   }

   public ModularRawSensorReader(String name, String description)
   {
      this.description = description;
      this.registry = new YoVariableRegistry(name);
   }

   public void addRawSensorReader(RawSensorReader rawSensorReader)
   {
      this.rawSensorReaders.add(rawSensorReader);
      this.registry.addChild(rawSensorReader.getYoVariableRegistry());
   }

   public void initialize()
   {
      for (RawSensorReader rawSensorReader : rawSensorReaders)
         rawSensorReader.initialize();
   }

   public void read()
   {
      for (RawSensorReader rawSensorReader : rawSensorReaders)
         rawSensorReader.read();
   }

   public YoVariableRegistry getYoVariableRegistry()
   {
      return registry;
   }

   public String getName()
   {
      return registry.getName();
   }

   public String getDescription()
   {
      return description;
   }

   @Override
   public String toString()
   {
      StringBuffer buf = new StringBuffer();
      for (RawSensorReader rawSensorReader : rawSensorReaders)
      {
         buf.append(rawSensorReader.getClass().getSimpleName() + "\n");
      }
      return buf.toString();
   }
}
