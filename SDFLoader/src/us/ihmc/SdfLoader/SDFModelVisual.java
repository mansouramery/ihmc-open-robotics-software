package us.ihmc.SdfLoader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import us.ihmc.graphics3DAdapter.graphics.Graphics3DObject;
import us.ihmc.robotics.geometry.RigidBodyTransform;

public class SDFModelVisual extends Graphics3DObject
{
   private final List<String> resourceDirectories;
   
   private final HashSet<SDFLinkHolder> addedLinks = new HashSet<>();
   
   public SDFModelVisual(GeneralizedSDFRobotModel generalizedSDFRobotModel)
   {
      this(generalizedSDFRobotModel, false);
   }
   
   public SDFModelVisual(GeneralizedSDFRobotModel generalizedSDFRobotModel, boolean useCollisionMeshes)
   {
      resourceDirectories = generalizedSDFRobotModel.getResourceDirectories();
      ArrayList<SDFLinkHolder> rootLinks = generalizedSDFRobotModel.getRootLinks();
      
      RigidBodyTransform modelTransform = generalizedSDFRobotModel.getTransformToRoot();
      for(SDFLinkHolder link : rootLinks)
      {  
         recursivelyAddLinks(link, modelTransform, useCollisionMeshes);
      }
   }
   
   private void recursivelyAddLinks(SDFLinkHolder link, RigidBodyTransform modelTransform, boolean useCollisionMeshes)
   {
      if(addedLinks.contains(link))
      {
         return;
      }
      addedLinks.add(link);
      
      if(link.getVisuals() != null)
      {
         
         RigidBodyTransform transformToModel = new RigidBodyTransform(modelTransform);
         transformToModel.multiply(link.getTransformFromModelReferenceFrame());
         
         SDFGraphics3DObject sdfGraphics3DObject;
         if(useCollisionMeshes)
         {
            sdfGraphics3DObject = new SDFGraphics3DObject(link.getCollisions(), resourceDirectories, transformToModel);
         }
         else
         {
            sdfGraphics3DObject = new SDFGraphics3DObject(link.getVisuals(), resourceDirectories, transformToModel);
         }
         getGraphics3DInstructions().addAll(sdfGraphics3DObject.getGraphics3DInstructions());
         
      }
      
      for(SDFJointHolder joint: link.getChildren())
      {
         recursivelyAddLinks(joint.getChildLinkHolder(), modelTransform, useCollisionMeshes);
      }
   }
}
