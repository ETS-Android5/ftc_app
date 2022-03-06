package org.firstinspires.ftc.teamcode.opmodes.auto;

import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.teamcode.hardware.Intake;
import org.firstinspires.ftc.teamcode.hardware.Lift;
import org.firstinspires.ftc.teamcode.hardware.Robot;
import org.firstinspires.ftc.teamcode.opmodes.teleop.ControlMgr;
import org.firstinspires.ftc.teamcode.util.Logger;
import org.firstinspires.ftc.teamcode.util.Status;
import org.firstinspires.ftc.teamcode.util.websocket.InetSocketServer;
import org.firstinspires.ftc.teamcode.util.websocket.Server;
import org.opencv.android.OpenCVLoader;

import java.io.IOException;
import java.nio.ByteBuffer;

public class AutonomousTemplate {
    String name;
    private final Robot robot;
    private Server server;
    private final Intake intake;
    private final Lift lift;
    private final ControlMgr control_mgr;
    public Logger logger;

    public int shipping_height = 0;

    private double y_distance = 0;

    private ElapsedTime lift_timer;
    public ElapsedTime lift_dumped_timer;
    private int id = 0;
    public int height = -1;
    public int adjusted_cycle = 0;

    public boolean dump_trigger = false;

    static
    {
        OpenCVLoader.initDebug();
    }

    public AutonomousTemplate(String name, Robot robot, HardwareMap hardware_map, Gamepad gamepad1, Gamepad gamepad2){
        this.name = name;
        this.robot = Robot.initialize(hardware_map, "Autonomous", 0);
        this.logger = new Logger(name);
        this.control_mgr = new ControlMgr(robot, gamepad1, gamepad2);

        lift = robot.lift;
        intake = robot.intake;
    }

    public void init_server(){
        try
        {
            server = new Server(new InetSocketServer(20000));
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        server.registerProcessor(0x01, (cmd, payload, resp) -> {
            ByteBuffer buf = ByteBuffer.allocate(500);

            buf.putDouble(distance());
            buf.putDouble(robot.lift.getLiftCurrentPos());
            buf.putDouble(robot.lift.getPower());
            if (robot.lineFinder.lineFound()){
                buf.putDouble(1.0);
            } else {
                buf.putDouble(-1.0);
            }

            buf.flip();
            resp.respond(buf);
        });
        server.startServer();
    }

    public void init_lift(){
        lift.extend(0, false);
        lift.rotate(Status.ROTATIONS.get("auto in"));
        robot.intake.deposit(Status.DEPOSITS.get("carry"));
        robot.lift.moveOutrigger(Status.OUTRIGGERS.get("up"));
        lift_timer = new ElapsedTime();
        lift_dumped_timer = new ElapsedTime();

        robot.lineFinder.initialize();
    }

    public void getShippingHeight(){
        double left_distance = robot.detector.getDistances()[0];
        double right_distance = robot.detector.getDistances()[1];
        if (right_distance >= 50 && right_distance <= 70){
            shipping_height = 3; // Checks 3 first to put in high goal as last resort
        } else if (left_distance >= 50 && left_distance <= 70){
            shipping_height = 1;
        } else {
            shipping_height = 2;
        }
        logger.i("Shipping Height: %d", shipping_height);
    }

    public void liftSequence(){
        switch (id) {
            case 0:
                switch (height){
                    case 1:
                        lift.rotate(Status.ROTATIONS.get("low_out"));
                        break;
                    case 2:
                        lift.rotate(Status.ROTATIONS.get("mid_out"));
                        break;
                    case 3:
                        lift.rotate(Status.ROTATIONS.get("high_out"));
                        break;
                    case 4:
                        lift.rotate(Status.ROTATIONS.get("neutral_out"));
                        break;
                    case 5:
                        lift.rotate(Status.ROTATIONS.get("high_out2"));
                        break;
                    case 6:
                        lift.rotate(Status.ROTATIONS.get("high_out"));
                        lift.moveOutrigger(Status.OUTRIGGERS.get("down"));
                        break;
                }

                double target_height = lift.getLiftCurrentPos();
                switch (height){
                    case 0:
                        target_height = 0;
                        break;
                    case 1:
                        target_height = Status.STAGES.get("low");
                        break;
                    case 2:
                        target_height = Status.STAGES.get("mid");
                        break;
                    case 3:
                        target_height = Status.STAGES.get("high") + adjusted_cycle;
                        break;
                    case 4:
                        target_height = Status.STAGES.get("neutral");
                        break;
                    case 5:
                        target_height = Status.STAGES.get("high2");
                        break;
                    case 6:
                        target_height = Status.STAGES.get("really high");
                        break;
                }
                lift.extend(target_height, true);
                if (lift.ifReached(target_height)){
                    logger.i("Reached Lift Height");
                    id += 1;
                    lift_timer.reset();
                }
                break;
            case 1:
                intake.deposit(Status.DEPOSITS.get("dump"));
                if (lift_timer.seconds() > 0.24) {
                    lift_timer.reset();
                    id += 1;
                }
                break;
            case 2:
                intake.deposit(Status.DEPOSITS.get("carry"));
                if (lift_timer.seconds() > Status.AUTO_DEPOSIT_TIME) {
                    lift_timer.reset();
                    id += 1;
                }
                break;
            case 3:
                lift.rotate(Status.ROTATIONS.get("in"));
                lift.extend(0, true);
                if (lift.ifReached(0)) {
                    id += 1;
                }
                break;
            case 4:
                height = -1;
                id = 0;
                break;
        }
    }

    public double distance() {
        double average = (robot.drivetrain.back_right.getCurrentPosition() + robot.drivetrain.back_left.getCurrentPosition() + robot.drivetrain.front_left.getCurrentPosition() + robot.drivetrain.front_right.getCurrentPosition()) / 4.0;
        return Math.abs(average * Status.inches_per_tick);
    }

    public void update() {
        if (height > -1){
            liftSequence();
        }

        lift.updateLift();
    }

    public void stop(){
        control_mgr.stop();
        if (server != null) {
            server.close();
        }
    }
}
