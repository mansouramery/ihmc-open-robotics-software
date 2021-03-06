package us.ihmc.simulationconstructionset;

import javax.vecmath.Vector3d;

import us.ihmc.robotics.Axis;

/**
 * Title:        Yobotics! Simulation Construction Set<p>
 * Description:  Package for Simulating Dynamic Robots and Mechanisms<p>
 * Copyright:    Copyright (c) Jerry Pratt<p>
 * Company:      Yobotics, Inc. <p>
 * @author Jerry Pratt
 * @version Beta 1.0
 */


public class CylinderJoint extends PinJoint
{
   /**
    *
    */
   private static final long serialVersionUID = -1979031212754263175L;
   private SliderJoint joint2;

   public CylinderJoint(String rotName, String transName, Vector3d offset, Robot rob, Axis jaxis)
   {
      super(rotName, offset, rob, jaxis);

      joint2 = new SliderJoint(transName, new Vector3d(), rob, jaxis);

      // super.addJoint(joint2); // This crashes.  Instead, add the joint manually:

      joint2.parentJoint = this;
      childrenJoints.add(joint2);
      

      // Set the child r_in value:

      joint2.physics.r_in.x = 0.0;
      joint2.physics.r_in.y = 0.0;
      joint2.physics.r_in.z = 0.0;
   }


   public void addJoint(Joint nextJoint)
   {
      joint2.addJoint(nextJoint);
   }

   public void setLink(Link l)
   {
      // Set this joints real link to a null link and set the second Joints link to the given link...
      Link nullLink = new Link("null");    // smallPiece();
      nullLink.setMass(0.0);
      nullLink.setMomentOfInertia(0.0, 0.0, 0.0);
      nullLink.setComOffset(0.0, 0.0, 0.0);

      super.setLink(nullLink);
      joint2.setLink(l);
   }

   public void addCameraMount(CameraMount mount)
   {
      joint2.addCameraMount(mount);
   }
   
   public void addIMUMount(IMUMount mount)
   {
      joint2.addIMUMount(mount);
   }

   public void addKinematicPoint(KinematicPoint point)
   {
      joint2.addKinematicPoint(point);
   }

   public void addGroundContactPoint(GroundContactPoint point)
   {
      joint2.addGroundContactPoint(point);
   }

   public void addExternalForcePoint(ExternalForcePoint point)
   {
      joint2.addExternalForcePoint(point);
   }

   public void setLimitStops(int axis, double q_min, double q_max, double k_limit, double b_limit)
   {
      if (axis == 1)
         super.setLimitStops(q_min, q_max, k_limit, b_limit);
      else if (axis == 2)
         joint2.setLimitStops(q_min, q_max, k_limit, b_limit);
   }

   public void setDamping(int axis, double b_damp)
   {
      if (axis == 1)
         super.setDamping(b_damp);
      else if (axis == 2)
         joint2.setDamping(b_damp);
   }

   public void setInitialState(double q1_init, double qd1_init, double q2_init, double qd2_init)
   {
      super.setInitialState(q1_init, qd1_init);
      joint2.setInitialState(q2_init, qd2_init);
   }

   public void getState(double[] state)
   {
      state[0] = q.getDoubleValue();
      state[1] = qd.getDoubleValue();
      state[2] = joint2.q.getDoubleValue();
      state[3] = joint2.qd.getDoubleValue();
   }


}
