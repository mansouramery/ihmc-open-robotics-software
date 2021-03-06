package optiTrack;

import java.util.ArrayList;

import javax.vecmath.Point3d;
import javax.vecmath.Quat4d;
import javax.vecmath.Vector3d;

//import us.ihmc.userInterface.ThirdPersonPerspective;
import us.ihmc.robotics.dataStructures.registry.YoVariableRegistry;
import us.ihmc.robotics.geometry.FramePose;
import us.ihmc.robotics.referenceFrames.ReferenceFrame;
import us.ihmc.robotics.time.CallFrequencyCalculator;
import us.ihmc.robotics.geometry.RigidBodyTransform;

public class IHMCMocapClient extends MocapDataClient
{
   private static IHMCMocapClient ihmcMocapClientSingleton;
//   protected ThirdPersonPerspective thirdPersonPerspective;
   ReferenceFrame mocapOrigin;
   ReferenceFrame mocapRB;
   ReferenceFrame mocapRBZUp;

   RigidBodyTransform rigidBodyInformationInMocapOrigin;

   CallFrequencyCalculator callFrequencyCalculator;

   public IHMCMocapClient()
   {
      callFrequencyCalculator = new CallFrequencyCalculator(new YoVariableRegistry("Registry"), "MOCAP_");
      mocapOrigin = new ReferenceFrame("mocapOrigin", ReferenceFrame.getWorldFrame())
      {
         @Override
         protected void updateTransformToParent(RigidBodyTransform transformToParent)
         {
            transformToParent.setRotationEulerAndZeroTranslation(Math.toRadians(-90), 0, 0);
            setTransformToParent(transformToParent);
         }
      };

      mocapOrigin.update();

      mocapRB = new ReferenceFrame("mocapRb", mocapOrigin)
      {
         @Override
         protected void updateTransformToParent(RigidBodyTransform transformToParent)
         {
            setTransformToParent(rigidBodyInformationInMocapOrigin);
         }
      };

      mocapRBZUp = new ReferenceFrame("mocapRbZUp", mocapRB)
      {
         @Override
         protected void updateTransformToParent(RigidBodyTransform transformToParent)
         {
            transformToParent.setRotationEulerAndZeroTranslation(Math.toRadians(90), 0, 0);
            setTransformToParent(transformToParent);
         }
      };
   }


   public static IHMCMocapClient getInstance() throws Exception
   {
      if (ihmcMocapClientSingleton == null)
      {
         ihmcMocapClientSingleton = new IHMCMocapClient();
      }

      return ihmcMocapClientSingleton;
   }

   public static void main(String args[])
   {
      new IHMCMocapClient();
   }

   ArrayList<MocapRigidBody> listOfConvertedRigidBodies;

   @Override
   protected void updateRigidBodiesListeners(ArrayList<MocapRigidBody> listOfRigidbodies)
   {
      frequency = callFrequencyCalculator.determineCallFrequency();

      if ((System.currentTimeMillis() - lastTime > 5000) && (frequency < 95))
      {
         System.err.println("**MOCAP WARNING** - Receiving data rate is less than 95Hz >>>> " + frequency);
         lastTime = System.currentTimeMillis();
      }

      ArrayList<MocapRigidbodiesListener> list = (ArrayList<MocapRigidbodiesListener>) listOfMocapRigidBodiesListeners.clone();
      for (MocapRigidbodiesListener listener : list)
      {
         updateRigidbodies(listOfRigidbodies);
      }
   }


   public void updateRigidbodies(ArrayList<MocapRigidBody> listOfRigidbodies)
   {
      listOfConvertedRigidBodies = new ArrayList<>();

      for (MocapRigidBody rb : listOfRigidbodies)
      {
//         if (rb.getId() == 1)
//         {
            rigidBodyInformationInMocapOrigin = new RigidBodyTransform(new Quat4d(rb.qx, rb.qy, rb.qz, rb.qw),
                    new Vector3d(rb.xPosition, rb.yPosition, rb.zPosition));
            mocapRB.update();
            mocapRBZUp.update();

            FramePose rigidBodyPoseInZUp = new FramePose(mocapRBZUp, new Point3d(0, 0, 0), new Quat4d());
            rigidBodyPoseInZUp.changeFrame(ReferenceFrame.getWorldFrame());

            final Vector3d position = new Vector3d();
            rigidBodyPoseInZUp.getPosition(position);

            final Quat4d rotation = new Quat4d();
            rigidBodyPoseInZUp.getOrientation(rotation);

            listOfConvertedRigidBodies.add(new MocapRigidBody(rb.getId(), position, rotation, null, true));
//         }
      }

      for (MocapRigidbodiesListener listener : listOfMocapRigidBodiesListeners)
      {
         listener.updateRigidbodies(listOfConvertedRigidBodies);
      }
   }
}
