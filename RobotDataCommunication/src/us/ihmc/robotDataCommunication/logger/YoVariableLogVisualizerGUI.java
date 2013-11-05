package us.ihmc.robotDataCommunication.logger;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.yobotics.simulationconstructionset.LongYoVariable;
import com.yobotics.simulationconstructionset.SimulationConstructionSet;
import com.yobotics.simulationconstructionset.UnreasonableAccelerationException;
import com.yobotics.simulationconstructionset.VariableChangedListener;
import com.yobotics.simulationconstructionset.YoVariable;
import com.yobotics.simulationconstructionset.movies.MovieFileFilter;

public class YoVariableLogVisualizerGUI extends JPanel
{
   private static final long serialVersionUID = 6414551517161321596L;

   private final MultiVideoDataPlayer multiPlayer;
   private final YoVariableLogPlaybackRobot robot;
   private final SimulationConstructionSet scs;

   public YoVariableLogVisualizerGUI(MultiVideoDataPlayer player, YoVariableLogPlaybackRobot robot, SimulationConstructionSet scs)
   {
      super();

      this.multiPlayer = player;
      this.robot = robot;
      this.scs = scs;

      setLayout(new GridLayout(1, 2));

      addGUIElements();

      setVisible(true);
   }

   private void switchVideoUpdate(String videoName)
   {
      if (multiPlayer != null) multiPlayer.setActivePlayer(videoName);
      
   }

   private void seek(int newValue)
   {
      if (!scs.isSimulating())
      {
         robot.seek(newValue);

         try
         {
            scs.simulateOneRecordStepNow();
            scs.setInPoint();
         }
         catch (UnreasonableAccelerationException e)
         {
            e.printStackTrace();
         }

         multiPlayer.indexChanged(0, 0);
      }
   }
   

   private void exportVideo()
   {
      if(multiPlayer != null)
      {
         
         if(scs.isSimulating())
         {
            scs.stop();
         }
         
         scs.gotoInPointNow();
         long startTimestamp = multiPlayer.getCurrentTimestamp();
         scs.gotoOutPointNow();
         long endTimestamp = multiPlayer.getCurrentTimestamp();
         
         if(startTimestamp > endTimestamp)
         {
            
            JOptionPane.showMessageDialog(this,
                  "startTimestamp > endTimestamp. Please set the in-point and out-point correctly",
                  "Timestmap error",
                  JOptionPane.ERROR_MESSAGE);
            
            return;
         }
         
         
         JFileChooser saveDialog = new JFileChooser(System.getProperty("user.home"));
         saveDialog.setFileFilter(new MovieFileFilter());
         File selectedFile = null;
         if (JFileChooser.APPROVE_OPTION == saveDialog.showSaveDialog(this))
         {
            selectedFile = saveDialog.getSelectedFile();
            
            multiPlayer.exportCurrentVideo(selectedFile, startTimestamp, endTimestamp);
            
         }
         
         
      }
      
   }

   private void addGUIElements()
   {
      
      
      final JComboBox<String> videoFiles = new JComboBox<>();
      videoFiles.addItem("-- Disabled --");
      for(String video : multiPlayer.getVideos())
      {
         videoFiles.addItem(video);
      }
      
      videoFiles.addActionListener(new ActionListener()
      {
         
         @Override
         public void actionPerformed(ActionEvent e)
         {
            if(videoFiles.getSelectedIndex() == 0)
            {
               switchVideoUpdate(null);
            }
            else
            {
               switchVideoUpdate((String) videoFiles.getSelectedItem());
            }
         }
      });
      videoFiles.setSelectedIndex(1);

      add(videoFiles);
//    timePanel.add(new JSeparator(JSeparator.VERTICAL));

      JPanel timePanel = new JPanel();
      timePanel.setLayout(new BorderLayout());
      timePanel.add(new JLabel("Position: "), BorderLayout.WEST);

//    timePanel.add(new JLabel(String.valueOf(robot.getInitialTimestamp())));
      final JSlider slider = new JSlider(0, robot.getNumberOfEntries(), 0);

      final JLabel currentTime = new JLabel(String.valueOf(robot.getFinalTimestamp()));

      slider.addChangeListener(new ChangeListener()
      {
         @Override
         public void stateChanged(ChangeEvent e)
         {
            currentTime.setText(slider.getValue() + "");
            seek(slider.getValue());
         }
      });

      robot.addCurrentRecordTickListener(new VariableChangedListener()
      {
         @Override
         public void variableChanged(YoVariable v)
         {
            if (scs.isSimulating())
            {
               slider.setValue((int) ((LongYoVariable) v).getLongValue());
            }
         }
      });
      
      
      final JButton exportVideo = new JButton("Export video");
      exportVideo.setToolTipText("Export video starting from the in point till the out point.");
      exportVideo.addActionListener(new ActionListener()
      {
         
         @Override
         public void actionPerformed(ActionEvent e)
         {
            exportVideo();
         }

      });
      
      timePanel.add(slider, BorderLayout.CENTER);
      timePanel.add(currentTime, BorderLayout.EAST);
      
      add(timePanel);
      add(exportVideo);
      
   }


}
