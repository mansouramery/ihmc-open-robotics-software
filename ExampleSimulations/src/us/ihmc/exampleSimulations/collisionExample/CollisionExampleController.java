package us.ihmc.exampleSimulations.collisionExample;

import javax.vecmath.Matrix3d;
import javax.vecmath.Vector3d;

import us.ihmc.robotics.dataStructures.registry.YoVariableRegistry;
import us.ihmc.robotics.dataStructures.variable.DoubleYoVariable;
import us.ihmc.simulationconstructionset.CollisionIntegrator;
import us.ihmc.simulationconstructionset.robotController.RobotController;


public class CollisionExampleController implements RobotController
{
   private static final long serialVersionUID = 5935306803939256821L;

   YoVariableRegistry registry = new YoVariableRegistry("CollisionExampleController");

   DoubleYoVariable q_ball1_x, q_ball1_y, q_ball1_z, qd_ball1_x, qd_ball1_y, qd_ball1_z, qd_ball1_wx, qd_ball1_wy, qd_ball1_wz;
   DoubleYoVariable q_ball2_x, q_ball2_y, q_ball2_z, qd_ball2_x, qd_ball2_y, qd_ball2_z, qd_ball2_wx, qd_ball2_wy, qd_ball2_wz;

   private CollisionExampleRobot rob;
   double mu = 0.9, epsilon = 0.9;

   private final DoubleYoVariable collision = new DoubleYoVariable("collision", registry);
   private final DoubleYoVariable energy = new DoubleYoVariable("energy", registry);

   public CollisionExampleController(CollisionExampleRobot rob)
   {
      this.rob = rob;

      setupControl();
   }


   public void setupControl()
   {
      q_ball1_x = (DoubleYoVariable) rob.getVariable("q_ball1_x");
      q_ball1_y = (DoubleYoVariable) rob.getVariable("q_ball1_y");
      q_ball1_z = (DoubleYoVariable) rob.getVariable("q_ball1_z");
      qd_ball1_x = (DoubleYoVariable) rob.getVariable("qd_ball1_x");
      qd_ball1_y = (DoubleYoVariable) rob.getVariable("qd_ball1_y");
      qd_ball1_z = (DoubleYoVariable) rob.getVariable("qd_ball1_z");
      qd_ball1_wx = (DoubleYoVariable) rob.getVariable("qd_ball1_wx");
      qd_ball1_wy = (DoubleYoVariable) rob.getVariable("qd_ball1_wy");
      qd_ball1_wz = (DoubleYoVariable) rob.getVariable("qd_ball1_wz");

      q_ball2_x = (DoubleYoVariable) rob.getVariable("q_ball2_x");
      q_ball2_y = (DoubleYoVariable) rob.getVariable("q_ball2_y");
      q_ball2_z = (DoubleYoVariable) rob.getVariable("q_ball2_z");
      qd_ball2_x = (DoubleYoVariable) rob.getVariable("qd_ball2_x");
      qd_ball2_y = (DoubleYoVariable) rob.getVariable("qd_ball2_y");
      qd_ball2_z = (DoubleYoVariable) rob.getVariable("qd_ball2_z");
      qd_ball2_wx = (DoubleYoVariable) rob.getVariable("qd_ball2_wx");
      qd_ball2_wy = (DoubleYoVariable) rob.getVariable("qd_ball2_wy");
      qd_ball2_wz = (DoubleYoVariable) rob.getVariable("qd_ball2_wz");


      I1_world.m00 = CollisionExampleRobot.Ixx1;
      I1_world.m01 = 0.0;
      I1_world.m02 = 0.0;
      I1_world.m10 = 0.0;
      I1_world.m11 = CollisionExampleRobot.Iyy1;
      I1_world.m12 = 0.0;
      I1_world.m20 = 0.0;
      I1_world.m21 = 0.0;
      I1_world.m22 = CollisionExampleRobot.Izz1;

      I2_world.m00 = CollisionExampleRobot.Ixx2;
      I2_world.m01 = 0.0;
      I2_world.m02 = 0.0;
      I2_world.m10 = 0.0;
      I2_world.m11 = CollisionExampleRobot.Iyy2;
      I2_world.m12 = 0.0;
      I2_world.m20 = 0.0;
      I2_world.m21 = 0.0;
      I2_world.m22 = CollisionExampleRobot.Izz2;

      q_ball1_x.set(1.0);
      q_ball1_y.set(-0.3);
      qd_ball1_x.set(-1.0);

      I1_world_inv.set(I1_world);
      I1_world_inv.invert();
      I2_world_inv.set(I2_world);
      I2_world_inv.invert();

   }



   Vector3d x1_world = new Vector3d(), x2_world = new Vector3d();
   Vector3d u1_world = new Vector3d(), u2_world = new Vector3d();

   // Vector3d u1_world = new Vector3d(1.0,2.0,3.0), u2_world = new Vector3d(0.0,0.0,0.0);
   // double xRotation = Math.PI/2.0;

   Matrix3d R_world_collision = new Matrix3d(), R_collision_world = new Matrix3d();

   Matrix3d I1_world = new Matrix3d(), I2_world = new Matrix3d(), I1_world_inv = new Matrix3d(), I2_world_inv = new Matrix3d();

   Matrix3d I1 = new Matrix3d(), I2 = new Matrix3d(), I1_inv = new Matrix3d(), I2_inv = new Matrix3d();
   Vector3d r1 = new Vector3d(), r2 = new Vector3d();
   Vector3d u1 = new Vector3d(), u2 = new Vector3d(), u = new Vector3d();

   Vector3d e_world = new Vector3d(), r1_world = new Vector3d(), r2_world = new Vector3d();

   Matrix3d r1_twidle = new Matrix3d(), r2_twidle = new Matrix3d(), t1 = new Matrix3d(), t2 = new Matrix3d(), K = new Matrix3d(), K_inverse = new Matrix3d();

   Vector3d u_final = new Vector3d(), impulse = new Vector3d();

   Vector3d impulse_world = new Vector3d();
   Vector3d delta_u = new Vector3d(), delta_u_world = new Vector3d(), delta_u1_world = new Vector3d(), delta_w1_world = new Vector3d();
   Vector3d delta_u2_world = new Vector3d(), delta_w2_world = new Vector3d();

   Vector3d temp = new Vector3d(), momentum_world_before = new Vector3d(), momentum_world_after = new Vector3d();



   private void computeRotation(Matrix3d rot, Vector3d vec)
   {
      // Z axis points in opposite direction of vec...
      Vector3d zAxis = new Vector3d(-vec.x, -vec.y, -vec.z);
      zAxis.normalize();

      Vector3d yAxis = new Vector3d(0.0, 0.0, 1.0);

      if (yAxis.equals(zAxis))
      {
         yAxis = new Vector3d(0.0, 1.0, 0.0);
      }

      Vector3d xAxis = new Vector3d();
      xAxis.cross(yAxis, zAxis);
      xAxis.normalize();

      yAxis.cross(zAxis, xAxis);

      rot.m00 = xAxis.x;
      rot.m01 = yAxis.x;
      rot.m02 = zAxis.x;
      rot.m10 = xAxis.y;
      rot.m11 = yAxis.y;
      rot.m12 = zAxis.y;
      rot.m20 = xAxis.z;
      rot.m21 = yAxis.z;
      rot.m22 = zAxis.z;

   }



   public void doControl()
   {
      u1_world.set(qd_ball1_x.getDoubleValue(), qd_ball1_y.getDoubleValue(), qd_ball1_z.getDoubleValue());
      u2_world.set(qd_ball2_x.getDoubleValue(), qd_ball2_y.getDoubleValue(), qd_ball2_z.getDoubleValue());

      energy.set(0.5 * CollisionExampleRobot.M1 * u1_world.dot(u1_world) + 0.5 * CollisionExampleRobot.M2 * u2_world.dot(u2_world));

      x1_world.set(q_ball1_x.getDoubleValue(), q_ball1_y.getDoubleValue(), q_ball1_z.getDoubleValue());
      x2_world.set(q_ball2_x.getDoubleValue(), q_ball2_y.getDoubleValue(), q_ball2_z.getDoubleValue());

      e_world.set(x2_world);
      e_world.sub(x1_world);

      if (e_world.length() > CollisionExampleRobot.R1 + CollisionExampleRobot.R2)
      {
         collision.set(0.0);

         return;
      }

      collision.set(1.0);

      // Compute momentum before:
      momentum_world_before.set(u1_world);
      momentum_world_before.scale(CollisionExampleRobot.M1);
      temp.set(u2_world);
      temp.scale(CollisionExampleRobot.M2);
      momentum_world_before.add(temp);

      // Compute Rotation Matrix from world to collision.  z must be in direction between the two centers of spheres:


      computeRotation(R_world_collision, e_world);


      // R_world_collision.rotX(Math.PI/2.0);

      Vector3d offset_world = new Vector3d(0.0, 0.0, CollisionExampleRobot.R1 + CollisionExampleRobot.R2);
      R_world_collision.transform(offset_world);

      // Vector3d x1_world = new Vector3d();
      // Vector3d x2_world = new Vector3d(x1_world);
      // x2_world.sub(offset_world);

      // Find intersection point and offset vectors:

      // e_world = new Vector3d(x2_world);
      // e_world.sub(x1_world);

      r1_world.set(e_world);
      r1_world.scale(CollisionExampleRobot.R1 / (CollisionExampleRobot.R1 + CollisionExampleRobot.R2));
      r2_world.set(e_world);
      r2_world.scale(-CollisionExampleRobot.R2 / (CollisionExampleRobot.R1 + CollisionExampleRobot.R2));

      // Rotate everything into collision frame:

      R_collision_world.set(R_world_collision);
      R_collision_world.transpose();

      I1.mul(R_collision_world, I1_world);
      I1.mul(R_world_collision);

      I2.mul(R_collision_world, I2_world);
      I2.mul(R_world_collision);

      u1.set(u1_world);
      R_collision_world.transform(u1);

      u2.set(u2_world);
      R_collision_world.transform(u2);

      u.set(u1);
      u.sub(u2);


      r1.set(r1_world);
      R_collision_world.transform(r1);
      r2.set(r2_world);
      R_collision_world.transform(r2);


      // Compute the K Matrix:


      r1_twidle.set(new double[]
      {
         0.0, -r1.z, r1.y, r1.z, 0.0, -r1.x, -r1.y, r1.x, 0.0
      });
      r2_twidle.set(new double[]
      {
         0.0, -r2.z, r2.y, r2.z, 0.0, -r2.x, -r2.y, r2.x, 0.0
      });


      I1_inv.set(I1);
      I1_inv.invert();
      I2_inv.set(I2);
      I2_inv.invert();

      t1.set(r1_twidle);
      t1.mul(I1_inv);
      t1.mul(r1_twidle);
      t2.set(r2_twidle);
      t2.mul(I2_inv);
      t2.mul(r2_twidle);

      K.set(new double[]
      {
         1, 0, 0, 0, 1, 0, 0, 0, 1
      });
      K.mul(1.0 / CollisionExampleRobot.M1 + 1.0 / CollisionExampleRobot.M2);
      K.sub(t1);
      K.sub(t2);

      K_inverse.set(K);
      K_inverse.invert();

      // Report:

      /*
       * System.out.println("x1_world, x2_world:");System.out.println(x1_world); System.out.println(x2_world);
       * System.out.println("r1_world, r2_world:");System.out.println(r1_world); System.out.println(r2_world);
       * System.out.println("u1_world, u2_world:");System.out.println(u1_world); System.out.println(u2_world);
       *
       *
       * System.out.println("r1, r2:");System.out.println(r1); System.out.println(r2);
       * System.out.println("u1, u2:");System.out.println(u1); System.out.println(u2);
       *
       * System.out.println("u:");System.out.println(u);
       * System.out.println("K:");System.out.println(K);
       */


      // Integrate the collision:

      CollisionIntegrator collisionIntegrator = new CollisionIntegrator();
      collisionIntegrator.setup(K, u, epsilon, mu);

//    double[] output = new double[4];
      Vector3d output = new Vector3d();

      // System.out.println("Integrating the collision...");
      collisionIntegrator.integrate(output);

      // System.out.println("Done Integrating the collision...");

      u_final = new Vector3d(output);


      // System.out.println("u_final:");System.out.println(u_final);

      // Compute the impulse:

      delta_u = new Vector3d(u);
      delta_u.sub(u_final);

      // System.out.println("delta_u:");System.out.println(delta_u);

      delta_u_world = new Vector3d(delta_u);
      R_world_collision.transform(delta_u_world);

      // System.out.println("delta_u_world:");System.out.println(delta_u_world);

      impulse = new Vector3d(delta_u);
      K_inverse.transform(impulse);

      // System.out.println("impulse:");System.out.println(impulse);

      // Transform the impulse into world coordinates:

      impulse_world = new Vector3d(impulse);
      R_world_collision.transform(impulse_world);

      // System.out.println("impulse_world:");System.out.println(impulse_world);

      // Figure out the change in velocities of the masses:

      delta_u1_world = new Vector3d(impulse_world);
      delta_u1_world.scale(1.0 / CollisionExampleRobot.M1);
      u1_world.sub(delta_u1_world);

      delta_w1_world = new Vector3d();
      delta_w1_world.cross(r1_world, impulse_world);
      I1_world_inv.transform(delta_w1_world);


      delta_u2_world = new Vector3d(impulse_world);
      delta_u2_world.scale(-1.0 / CollisionExampleRobot.M2);
      u2_world.sub(delta_u2_world);

      delta_w2_world = new Vector3d();
      delta_w2_world.cross(r2_world, impulse_world);
      I2_world_inv.transform(delta_w2_world);
      delta_w2_world.scale(-1.0);

      /*
       * System.out.println("I1_world_inv: ");
       * System.out.println(I1_world_inv);
       *
       * System.out.println("I2_world_inv: ");
       * System.out.println(I2_world_inv);
       */



      // System.out.println("u1_world, u2_world after collision:  "); System.out.println(u1_world); System.out.println(u2_world);
      // System.out.println("delta_w1_world, delta_w2_world after collision:  "); System.out.println(delta_w1_world); System.out.println(delta_w2_world);


      momentum_world_after = new Vector3d(u1_world);
      momentum_world_after.scale(CollisionExampleRobot.M1);
      temp = new Vector3d(u2_world);
      temp.scale(CollisionExampleRobot.M2);
      momentum_world_after.add(temp);

      // System.out.println("Total momentum_world before, after:  ");System.out.println(momentum_world_before);System.out.println(momentum_world_after);

      double energy_world_after = 0.5 * u1_world.dot(u1_world) + 0.5 * u2_world.dot(u2_world);

      // Set the new values to the masses:
      qd_ball1_x.set(u1_world.x);
      qd_ball1_y.set(u1_world.y);
      qd_ball1_z.set(u1_world.z);
      qd_ball2_x.set(u2_world.x);
      qd_ball2_y.set(u2_world.y);
      qd_ball2_z.set(u2_world.z);

      qd_ball1_wx.set(-delta_w1_world.x);
      qd_ball1_wy.set(-delta_w1_world.y);
      qd_ball1_wz.set(-delta_w1_world.z);

      qd_ball2_wx.set(-delta_w2_world.x);
      qd_ball2_wy.set(-delta_w2_world.y);
      qd_ball2_wz.set(-delta_w2_world.z);



      // System.out.println("Total energy_world before, after:  ");System.out.println(energy_world_before);System.out.println(energy_world_after);

   }

   public void initialize()
   {
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
      return getName();
   }

}
