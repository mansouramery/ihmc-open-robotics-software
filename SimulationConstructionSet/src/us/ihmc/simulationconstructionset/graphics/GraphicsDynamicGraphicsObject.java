package us.ihmc.simulationconstructionset.graphics;

import us.ihmc.graphics3DAdapter.structure.Graphics3DNode;
import us.ihmc.graphics3DAdapter.structure.Graphics3DNodeType;
import us.ihmc.robotics.geometry.RigidBodyTransform;
import us.ihmc.simulationconstructionset.yoUtilities.graphics.YoGraphic;
import us.ihmc.tools.gui.GraphicsUpdatable;
import us.ihmc.tools.thread.CloseableAndDisposable;
import us.ihmc.tools.thread.CloseableAndDisposableRegistry;


public class GraphicsDynamicGraphicsObject extends Graphics3DNode implements GraphicsUpdatable, CloseableAndDisposable
{
   private YoGraphic yoGraphic;

   public GraphicsDynamicGraphicsObject(YoGraphic yoGraphic, CloseableAndDisposableRegistry closeableAndDisposableRegistry)
   {
      super(yoGraphic.getName(), Graphics3DNodeType.VISUALIZATION);
      this.yoGraphic = yoGraphic;
      setGraphicsObject(yoGraphic.getLinkGraphics());
      update();
      
      if (closeableAndDisposableRegistry != null) closeableAndDisposableRegistry.registerCloseableAndDisposable(this);
   }

   public void update()
   {
      // IMPORTANT: can't do this here because it causes threading issues. Each thread is responsible for updating its own DynamicGraphicObjects!
//      yoGraphic.update();
      
      RigidBodyTransform j3dTransform = yoGraphic.getTransform();
      setTransform(j3dTransform);
   }

   @Override
   public void closeAndDispose()
   {
      yoGraphic = null;
      
   }


}
