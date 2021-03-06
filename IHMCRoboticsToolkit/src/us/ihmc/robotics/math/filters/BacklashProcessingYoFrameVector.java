package us.ihmc.robotics.math.filters;

import us.ihmc.robotics.dataStructures.registry.YoVariableRegistry;
import us.ihmc.robotics.dataStructures.variable.DoubleYoVariable;
import us.ihmc.robotics.math.frames.YoFrameTuple;
import us.ihmc.robotics.math.frames.YoFrameVariableNameTools;
import us.ihmc.robotics.math.frames.YoFrameVector;
import us.ihmc.robotics.referenceFrames.ReferenceFrame;

public class BacklashProcessingYoFrameVector extends YoFrameVector implements ProcessingYoVariable
{
   private final BacklashProcessingYoVariable xDot, yDot, zDot;

   public static BacklashProcessingYoFrameVector createBacklashProcessingYoFrameVector(String namePrefix, String nameSuffix, double dt, DoubleYoVariable slopTime,
           YoVariableRegistry registry, YoFrameTuple<?, ?> yoFrameTupleToProcess)
   {
      String xName = YoFrameVariableNameTools.createXName(namePrefix, nameSuffix);
      String yName = YoFrameVariableNameTools.createYName(namePrefix, nameSuffix);
      String zName = YoFrameVariableNameTools.createZName(namePrefix, nameSuffix);

      DoubleYoVariable xRaw = yoFrameTupleToProcess.getYoX();
      DoubleYoVariable yRaw = yoFrameTupleToProcess.getYoY();
      DoubleYoVariable zRaw = yoFrameTupleToProcess.getYoZ();

      BacklashProcessingYoVariable x = new BacklashProcessingYoVariable(xName, "", xRaw, dt, slopTime, registry);
      BacklashProcessingYoVariable y = new BacklashProcessingYoVariable(yName, "", yRaw, dt, slopTime, registry);
      BacklashProcessingYoVariable z = new BacklashProcessingYoVariable(zName, "", zRaw, dt, slopTime, registry);

      ReferenceFrame referenceFrame = yoFrameTupleToProcess.getReferenceFrame();

      return new BacklashProcessingYoFrameVector(x, y, z, registry, referenceFrame);
   }

   private BacklashProcessingYoFrameVector(BacklashProcessingYoVariable xDot, BacklashProcessingYoVariable yDot, BacklashProcessingYoVariable zDot,
           YoVariableRegistry registry, ReferenceFrame referenceFrame)
   {
      super(xDot, yDot, zDot, referenceFrame);

      this.xDot = xDot;
      this.yDot = yDot;
      this.zDot = zDot;
   }

   @Override
   public void update()
   {
      xDot.update();
      yDot.update();
      zDot.update();
   }

   public void reset()
   {
      xDot.reset();
      yDot.reset();
      zDot.reset();
   }
}
