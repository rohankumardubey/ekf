package us.ihmc.ekf.interfaces;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.ImmutablePair;

import us.ihmc.commons.PrintTools;
import us.ihmc.ekf.filter.sensor.AngularVelocitySensor;
import us.ihmc.ekf.filter.sensor.JointPositionSensor;
import us.ihmc.ekf.filter.sensor.Sensor;
import us.ihmc.euclid.tuple3D.Vector3D;
import us.ihmc.robotics.sensors.IMUDefinition;
import us.ihmc.simulationconstructionset.FloatingRootJointRobot;
import us.ihmc.simulationconstructionset.IMUMount;
import us.ihmc.simulationconstructionset.Joint;
import us.ihmc.simulationconstructionset.PinJoint;

public class SimulationSensorReader implements RobotSensorReader
{
   private final List<Sensor> allSensors = new ArrayList<>();
   private final List<ImmutablePair<PinJoint, JointPositionSensor>> jointPositionSensors = new ArrayList<>();
   private final List<ImmutablePair<IMUMount, AngularVelocitySensor>> angularVelocitySensors = new ArrayList<>();

   public SimulationSensorReader(FloatingRootJointRobot robot, FullRobotModel fullRobotModel)
   {
      addJointPositionSensorsForChildren(robot.getRootJoint(), fullRobotModel, jointPositionSensors);
      jointPositionSensors.stream().forEach(s -> allSensors.add(s.getRight()));

      fullRobotModel.getImuDefinitions().stream().forEach(imu -> addIMUSensor(imu, robot, fullRobotModel, angularVelocitySensors));
      angularVelocitySensors.stream().forEach(s -> allSensors.add(s.getRight()));
   }

   private static void addIMUSensor(IMUDefinition imu, FloatingRootJointRobot robot, FullRobotModel fullRobotModel,
                             List<ImmutablePair<IMUMount, AngularVelocitySensor>> angularVelocitySensors)
   {
      String imuName = imu.getName();
      IMUMount imuMount = robot.getIMUMount(imuName);

      if (imuMount == null)
      {
         throw new RuntimeException("Could not find IMU '" + imuName + "' in robot.");
      }

      AngularVelocitySensor sensor = new AngularVelocitySensor(imu, fullRobotModel);
      angularVelocitySensors.add(new ImmutablePair<IMUMount, AngularVelocitySensor>(imuMount, sensor));
      PrintTools.info("Created angular velocity sensor for IMU '" + imuName + "'");
   }

   private static void addJointPositionSensorsForChildren(Joint joint, FullRobotModel fullRobotModel,
                                                          List<ImmutablePair<PinJoint, JointPositionSensor>> sensors)
   {
      for (Joint child : joint.getChildrenJoints())
      {
         if (child instanceof PinJoint)
         {
            PinJoint pinJoint = (PinJoint) child;
            String jointName = pinJoint.getName();
//            if ("arm_1_arm_2".equals(jointName))
//            {
//               PrintTools.info("No joint position sensor for '" + jointName + "'. It is broken I guess.");
//            }
//            else
            {
               JointPositionSensor sensor = new JointPositionSensor(jointName, fullRobotModel);
               sensors.add(new ImmutablePair<>(pinJoint, sensor));
               PrintTools.info("Created joint position sensor for '" + jointName + "'");
            }
         }
         else
         {
            PrintTools.warn("Can not add joint position sensor for joint of type " + joint.getClass().getSimpleName());
         }

         addJointPositionSensorsForChildren(child, fullRobotModel, sensors);
      }
   }

   private final Vector3D tempVector = new Vector3D();

   @Override
   public void read()
   {
      for (ImmutablePair<PinJoint, JointPositionSensor> sensorPair : jointPositionSensors)
      {
         PinJoint joint = sensorPair.getLeft();
         JointPositionSensor sensor = sensorPair.getRight();
         sensor.setJointPositionMeasurement(joint.getQ());
      }

      for (ImmutablePair<IMUMount, AngularVelocitySensor> sensorPair : angularVelocitySensors)
      {
         IMUMount imuMount = sensorPair.getLeft();
         AngularVelocitySensor sensor = sensorPair.getRight();
         imuMount.getAngularVelocityInBody(tempVector);
         sensor.setAngularVelocityMeasurement(tempVector);
      }
   }

   @Override
   public List<Sensor> getSensors()
   {
      return allSensors;
   }

}
