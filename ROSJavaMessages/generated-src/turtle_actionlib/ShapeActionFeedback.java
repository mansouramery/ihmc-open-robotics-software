package turtle_actionlib;

public interface ShapeActionFeedback extends org.ros.internal.message.Message {
  static final java.lang.String _TYPE = "turtle_actionlib/ShapeActionFeedback";
  static final java.lang.String _DEFINITION = "# ====== DO NOT MODIFY! AUTOGENERATED FROM AN ACTION DEFINITION ======\n\nHeader header\nactionlib_msgs/GoalStatus status\nShapeFeedback feedback\n";
  std_msgs.Header getHeader();
  void setHeader(std_msgs.Header value);
  actionlib_msgs.GoalStatus getStatus();
  void setStatus(actionlib_msgs.GoalStatus value);
  turtle_actionlib.ShapeFeedback getFeedback();
  void setFeedback(turtle_actionlib.ShapeFeedback value);
}