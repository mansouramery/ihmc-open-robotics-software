package move_arm_head_monitor;

public interface HeadMonitorAction extends org.ros.internal.message.Message {
  static final java.lang.String _TYPE = "move_arm_head_monitor/HeadMonitorAction";
  static final java.lang.String _DEFINITION = "# ====== DO NOT MODIFY! AUTOGENERATED FROM AN ACTION DEFINITION ======\n\nHeadMonitorActionGoal action_goal\nHeadMonitorActionResult action_result\nHeadMonitorActionFeedback action_feedback\n";
  move_arm_head_monitor.HeadMonitorActionGoal getActionGoal();
  void setActionGoal(move_arm_head_monitor.HeadMonitorActionGoal value);
  move_arm_head_monitor.HeadMonitorActionResult getActionResult();
  void setActionResult(move_arm_head_monitor.HeadMonitorActionResult value);
  move_arm_head_monitor.HeadMonitorActionFeedback getActionFeedback();
  void setActionFeedback(move_arm_head_monitor.HeadMonitorActionFeedback value);
}