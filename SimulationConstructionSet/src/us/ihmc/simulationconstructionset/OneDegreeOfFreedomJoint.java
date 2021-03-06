package us.ihmc.simulationconstructionset;

import javax.vecmath.Vector3d;

import us.ihmc.robotics.dataStructures.variable.DoubleYoVariable;

public abstract class OneDegreeOfFreedomJoint extends Joint
{
   private static final long serialVersionUID = -462519687462777726L;

   /*
    * VRC HACK
    */
   private double kp = 0;
   private double kd = 0;
   private double qDesired = 0;
   private double qdDesired = 0;

   public OneDegreeOfFreedomJoint(String jname, Vector3d offset, Robot rob)
   {
      super(jname, offset, rob, 1);
   }

   public abstract DoubleYoVariable getQDD();

   public abstract DoubleYoVariable getQD();

   public abstract DoubleYoVariable getQ();

   public abstract void setQdd(double qdd);

   public abstract void setQd(double qd);

   public abstract void setQ(double q);

   public abstract void setTau(double tau);

   public abstract DoubleYoVariable getTau();

   public abstract double getDamping();

   public abstract void setDamping(double b_damp);

   public abstract double getTorqueLimit();

   public abstract double getVelocityLimit();

   public abstract double getJointUpperLimit();

   public abstract double getJointLowerLimit();

   public abstract double getJointStiction();

   /*
    * VRC HACK
    */
   public double getKp()
   {
      return kp;
   }

   public double getKd()
   {
      return kd;
   }

   public double getqDesired()
   {
      return qDesired;
   }

   public double getQdDesired()
   {
      return qdDesired;
   }

   public void setKp(double kp)
   {
      this.kp = kp;
   }

   public void setKd(double kd)
   {
      this.kd = kd;
   }

   public void setqDesired(double qDesired)
   {
      this.qDesired = qDesired;
   }

   public void setQdDesired(double qdDesired)
   {
      this.qdDesired = qdDesired;
   }

   public double doPDControl()
   {
      double qError = qDesired - getQ().getDoubleValue();
      double qdError = qdDesired - getQD().getDoubleValue();

      return kp * qError + kd * qdError;
   }
}
