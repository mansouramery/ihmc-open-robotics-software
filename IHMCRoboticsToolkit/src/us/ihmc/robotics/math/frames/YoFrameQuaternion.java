package us.ihmc.robotics.math.frames;

import javax.vecmath.*;

import us.ihmc.robotics.MathTools;
import us.ihmc.robotics.dataStructures.listener.VariableChangedListener;
import us.ihmc.robotics.dataStructures.registry.YoVariableRegistry;
import us.ihmc.robotics.dataStructures.variable.DoubleYoVariable;
import us.ihmc.robotics.geometry.FrameOrientation;
import us.ihmc.robotics.geometry.ReferenceFrameHolder;
import us.ihmc.robotics.referenceFrames.ReferenceFrame;
import us.ihmc.robotics.geometry.RotationFunctions;

public class YoFrameQuaternion extends ReferenceFrameHolder
{
   private final DoubleYoVariable qx, qy, qz, qs;
   private final Quat4d quaternion = new Quat4d();
   private final Quat4d tempQuaternion2 = new Quat4d();
   private final ReferenceFrame referenceFrame;

   public YoFrameQuaternion(String namePrefix, ReferenceFrame referenceFrame, YoVariableRegistry registry)
   {
      this(namePrefix, "", referenceFrame, registry);
   }

   public YoFrameQuaternion(String namePrefix, String nameSuffix, ReferenceFrame referenceFrame, YoVariableRegistry registry)
   {
      this.qx = new DoubleYoVariable(namePrefix + "Qx" + nameSuffix, registry);
      this.qy = new DoubleYoVariable(namePrefix + "Qy" + nameSuffix, registry);
      this.qz = new DoubleYoVariable(namePrefix + "Qz" + nameSuffix, registry);
      this.qs = new DoubleYoVariable(namePrefix + "Qs" + nameSuffix, registry);
      this.referenceFrame = referenceFrame;

      qs.set(1.0);
   }

   public YoFrameQuaternion(DoubleYoVariable qx, DoubleYoVariable qy, DoubleYoVariable qz, DoubleYoVariable qs, ReferenceFrame referenceFrame)
   {
      this.qx = qx;
      this.qy = qy;
      this.qz = qz;
      this.qs = qs;
      this.referenceFrame = referenceFrame;
   }

   public void set(Quat4d quat)
   {
      quaternion.set(quat);
      getYoValuesFromQuat4d();
   }

   public void set(Matrix3d matrix)
   {
      RotationFunctions.setQuaternionBasedOnMatrix3d(quaternion, matrix);
      getYoValuesFromQuat4d();
   }

   public void set(AxisAngle4d axisAngle)
   {
      quaternion.set(axisAngle);
      getYoValuesFromQuat4d();
   }

   public void set(double[] yawPitchRoll)
   {
      RotationFunctions.setQuaternionBasedOnYawPitchRoll(quaternion, yawPitchRoll);
      getYoValuesFromQuat4d();
   }

   public void set(double yaw, double pitch, double roll)
   {
      RotationFunctions.setQuaternionBasedOnYawPitchRoll(quaternion, yaw, pitch, roll);
      getYoValuesFromQuat4d();
   }

   public void set(FrameOrientation frameOrientation)
   {
      checkReferenceFrameMatch(frameOrientation);
      frameOrientation.getQuaternion(quaternion);
      getYoValuesFromQuat4d();
   }

   public void set(YoFrameQuaternion yoFrameQuaternion)
   {
      checkReferenceFrameMatch(yoFrameQuaternion);
      yoFrameQuaternion.get(quaternion);
      getYoValuesFromQuat4d();
   }

   public void get(Quat4d quat)
   {
      putYoValuesIntoQuat4d();
      quat.set(quaternion);
   }

//   public void get(Quat4f quat)
//   {
//      putYoValuesIntoQuat4d();
//      quat.set(quaternion);
//   }

   public void get(Matrix3d matrix)
   {
      putYoValuesIntoQuat4d();
      matrix.set(quaternion);
   }

   public void get(Matrix3f matrix)
   {
      putYoValuesIntoQuat4d();
      matrix.set(quaternion);
   }

   public void get(AxisAngle4d axisAngle)
   {
      putYoValuesIntoQuat4d();
      axisAngle.set(quaternion);
   }

   public void getYawPitchRoll(double[] yawPitchRoll)
   {
      putYoValuesIntoQuat4d();
      RotationFunctions.setYawPitchRollBasedOnQuaternion(yawPitchRoll, quaternion);
   }

   public void getFrameOrientationIncludingFrame(FrameOrientation frameOrientation)
   {
      putYoValuesIntoQuat4d();
      frameOrientation.setIncludingFrame(getReferenceFrame(), quaternion);
   }

   public void interpolate(YoFrameQuaternion yoFrameQuaternion1, YoFrameQuaternion yoFrameQuaternion2, double alpha)
   {
      checkReferenceFrameMatch(yoFrameQuaternion1);
      checkReferenceFrameMatch(yoFrameQuaternion2);
      alpha = MathTools.clipToMinMax(alpha, 0.0, 1.0);

      yoFrameQuaternion1.putYoValuesIntoQuat4d();
      yoFrameQuaternion2.putYoValuesIntoQuat4d();

      quaternion.interpolate(yoFrameQuaternion1.quaternion, yoFrameQuaternion2.quaternion, alpha); 
      checkQuaternionIsUnitMagnitude(quaternion);
      getYoValuesFromQuat4d();
   }


   public void checkQuaternionIsUnitMagnitude()
   {
      putYoValuesIntoQuat4d();
      checkQuaternionIsUnitMagnitude(this.quaternion);
   }
   
   private static void checkQuaternionIsUnitMagnitude(Quat4d quaternion)
   {
      double normSquared = (quaternion.x * quaternion.x + quaternion.y * quaternion.y + quaternion.z * quaternion.z + quaternion.w * quaternion.w);
      if (Math.abs(normSquared - 1.0) > 1e-12)
      {
         System.err.println("\nQuaternion " + quaternion + " is not unit magnitude! normSquared = " + normSquared);

         throw new RuntimeException("Quaternion " + quaternion + " is not unit magnitude! normSquared = " + normSquared);
      }
   }
   
   /**
    * Method used to concatenate the orientation represented by this YoFrameQuaternion and the orientation represented by the FrameOrientation.
    * @param quat4d
    */
   public void mul(Quat4d quat4d)
   {
      putYoValuesIntoQuat4d();
      quaternion.mul(quat4d);
      getYoValuesFromQuat4d();
   }

   /**
    * Method used to concatenate the orientation represented by this YoFrameQuaternion and the orientation represented by the FrameOrientation.
    * @param frameOrientation
    */
   public void mul(FrameOrientation frameOrientation)
   {
      checkReferenceFrameMatch(frameOrientation.getReferenceFrame());
      frameOrientation.getQuaternion(tempQuaternion2);
      mul(tempQuaternion2);
   }

   @Override
   public ReferenceFrame getReferenceFrame()
   {
      return referenceFrame;
   }

   private void getYoValuesFromQuat4d()
   {
      qx.set(quaternion.getX());
      qy.set(quaternion.getY());
      qz.set(quaternion.getZ());
      qs.set(quaternion.getW());
   }

   public void setToNaN()
   {
      qx.set(Double.NaN);
      qy.set(Double.NaN);
      qz.set(Double.NaN);
      qs.set(Double.NaN);
   }

   public boolean containsNaN()
   {
      return qx.isNaN() || qy.isNaN() || qz.isNaN() || qs.isNaN();
   }

   private void putYoValuesIntoQuat4d()
   {
      quaternion.set(qx.getDoubleValue(), qy.getDoubleValue(), qz.getDoubleValue(), qs.getDoubleValue());
   }

   public void attachVariableChangedListener(VariableChangedListener variableChangedListener)
   {
      qx.addVariableChangedListener(variableChangedListener);
      qy.addVariableChangedListener(variableChangedListener);
      qz.addVariableChangedListener(variableChangedListener);
      qs.addVariableChangedListener(variableChangedListener);
   }

   /**
    * toString
    *
    * String representation of a FrameVector (qx, qy, qz, qs)-reference frame name
    *
    * @return String
    */
   @Override
   public String toString()
   {
      return "(" + qx.getDoubleValue() + ", " + qy.getDoubleValue() + ", " + qz.getDoubleValue() + ", " + qs.getDoubleValue() + ")-" + getReferenceFrame();
   }

}