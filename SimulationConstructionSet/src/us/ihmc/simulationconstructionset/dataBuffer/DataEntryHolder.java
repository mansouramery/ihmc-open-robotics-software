package us.ihmc.simulationconstructionset.dataBuffer;

import us.ihmc.robotics.dataStructures.variable.YoVariable;

public interface DataEntryHolder
{
   public DataEntry getEntry(YoVariable<?> yoVariable);
}
