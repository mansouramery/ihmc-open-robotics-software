package us.ihmc.graphics3DAdapter;

import us.ihmc.robotics.geometry.Ray3d;

public interface RayCollisionAdapter
{
	public void setPickingGeometry(Ray3d ray3d);
	public double getPickDistance();
}