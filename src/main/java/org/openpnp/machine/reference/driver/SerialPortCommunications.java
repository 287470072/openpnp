package org.openpnp.machine.reference.driver;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import org.openpnp.gui.JobPanel;
import org.openpnp.gui.MainFrame;
import org.pmw.tinylog.Logger;
import org.simpleframework.xml.Attribute;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * A class for SerialPort Communications. Includes functions for connecting,
 * disconnecting, reading and sending lines.
 */
public class SerialPortCommunications extends ReferenceDriverCommunications {
    public enum DataBits {
        Five(5),
        Six(6),
        Seven(7),
        Eight(8);

        public final int mask;

        private DataBits(int mask) {
            this.mask = mask;
        }
    }

    public enum StopBits {
        One(SerialPort.ONE_STOP_BIT),
        OnePointFive(SerialPort.ONE_POINT_FIVE_STOP_BITS),
        Two(SerialPort.TWO_STOP_BITS);

        public final int mask;

        private StopBits(int mask) {
            this.mask = mask;
        }
    }

    public enum FlowControl {
        Off(SerialPort.FLOW_CONTROL_DISABLED),
        RtsCts(SerialPort.FLOW_CONTROL_CTS_ENABLED | SerialPort.FLOW_CONTROL_RTS_ENABLED),
        XonXoff(SerialPort.FLOW_CONTROL_XONXOFF_IN_ENABLED | SerialPort.FLOW_CONTROL_XONXOFF_OUT_ENABLED);

        public final int mask;

        private FlowControl(int mask) {
            this.mask = mask;
        }
    }

    public enum Parity {
        None(SerialPort.NO_PARITY),
        Mark(SerialPort.MARK_PARITY),
        Space(SerialPort.SPACE_PARITY),
        Even(SerialPort.EVEN_PARITY),
        Odd(SerialPort.ODD_PARITY);

        public final int mask;

        private Parity(int mask) {
            this.mask = mask;
        }
    }

    @Attribute(required = false)
    protected String portName = "";

    @Attribute(required = false)
    protected int baud = 115200;

    @Attribute(required = false)
    protected FlowControl flowControl = FlowControl.Off;

    @Attribute(required = false)
    protected DataBits dataBits = DataBits.Eight;

    @Attribute(required = false)
    protected StopBits stopBits = StopBits.One;

    @Attribute(required = false)
    protected Parity parity = Parity.None;

    @Attribute(required = false)
    protected boolean setDtr = false;

    @Attribute(required = false)
    protected boolean setRts = false;

    @Attribute(required = false)
    protected String name = "SerialPortCommunications";


    private SerialPort serialPort;

    public synchronized void connect() throws Exception {
        disconnect();
        serialPort = SerialPort.getCommPort(portName);
        serialPort.openPort(0);
        if (serialPort.isOpen()) {
            serialPort.addDataListener(new SerialPortDataListener() {
                @Override
                public int getListeningEvents() {
                    return SerialPort.LISTENING_EVENT_PORT_DISCONNECTED;
                    //返回要监听的事件类型，以供回调函数使用。可发回的事件包括：SerialPort.LISTENING_EVENT_DATA_AVAILABLE，SerialPort.LISTENING_EVENT_DATA_WRITTEN,SerialPort.LISTENING_EVENT_DATA_RECEIVED。分别对应有数据在串口（不论是读的还是写的），有数据写入串口，从串口读取数据。如果AVAILABLE和RECEIVED同时被监听，优先触发RECEIVED
                }

                @Override
                public void serialEvent(SerialPortEvent event) {
                    if (event.getEventType() == SerialPort.LISTENING_EVENT_PORT_DISCONNECTED) {
                        Logger.warn(serialPort.getSystemPortName() + "断开连接！！");
                        try {
                            disconnect();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            });
        }
        serialPort.setComPortParameters(baud, dataBits.mask, stopBits.mask, parity.mask);
        serialPort.setFlowControl(flowControl.mask);
        if (setDtr) {
            serialPort.setDTR();
        }
        if (setRts) {
            serialPort.setRTS();
        }
        serialPort.setComPortTimeouts(
                SerialPort.TIMEOUT_READ_SEMI_BLOCKING | SerialPort.TIMEOUT_WRITE_BLOCKING, 0, 0);


    }

    public synchronized void disconnect() throws Exception {
        if (serialPort != null && serialPort.isOpen()) {
            serialPort.closePort();
            serialPort = null;
        }
    }


    /**
     * Returns an array of Strings containing the names of serial ports
     * present on the system
     *
     * @return array of Strings of serial port names
     */
    public static String[] getPortNames() {
        SerialPort[] ports = SerialPort.getCommPorts();
        ArrayList<String> portNames = new ArrayList<>();
        for (SerialPort port : ports) {
            portNames.add(port.getSystemPortName());
        }
        return portNames.toArray(new String[]{});
    }

    public static Map<String, String> getPortDescribe() {
        Map<String, String> map = new HashMap<>();
        SerialPort[] ports = SerialPort.getCommPorts();
        ArrayList<String> portNames = new ArrayList<>();
        for (SerialPort port : ports) {
            portNames.add(port.getPortDescription());
            map.put(port.getPortDescription(), port.getSystemPortName());
        }
        return map;
    }

    //这部分是我自己加的->
    private static String byte2Hex(byte[] bytes) {
        StringBuffer stringBuffer = new StringBuffer();
        String temp = null;
        for (int i = 0; i < bytes.length; i++) {
            temp = Integer.toHexString(bytes[i] & 0xFF);
            if (temp.length() == 1) {
                //1得到一位的进行补0操作
                stringBuffer.append("0");
            }
            stringBuffer.append(temp);
        }
        return stringBuffer.toString();
    }
    //<-这部分是我自己加的

    @Override
    public int read() throws TimeoutException, IOException {
        byte[] b = new byte[1];
        int l;
        try {
            l = serialPort.readBytes(b, 1);

            switch (byte2Hex(b)) {
                case "fe":
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            JobPanel jobpaneStart = MainFrame.get().getJobTab();
                            MainFrame.get().getTabs().setSelectedComponent((Component) MainFrame.get().getJobTab());
                            jobpaneStart.start();
                        }
                    });
                    break;
                case "fd":
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            JobPanel jobpanelStop = MainFrame.get().getJobTab();
                            MainFrame.get().getTabs().setSelectedComponent((Component) MainFrame.get().getJobTab());
                            jobpanelStop.stop();
                        }
                    });
                    break;
                case "fc":
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            JobPanel jobpanelStep = MainFrame.get().getJobTab();
                            MainFrame.get().getTabs().setSelectedComponent((Component) MainFrame.get().getJobTab());
                            jobpanelStep.step();
                        }
                    });
                    break;
            }

            //Logger.trace("串口获取到数据{}", byte2Hex(b));
        } catch (NullPointerException e) {
            throw new IOException("Trying to read from a unconnected serial.");
        }
        if (l == -1) {
            throw new IOException("Read error.");
        }
        if (l == 0) {
            throw new TimeoutException("Read timeout.");
        }
        return b[0];
    }

    @Override
    public void writeBytes(byte[] data) throws IOException {
        int l = serialPort.writeBytes(data, data.length);
        if (l == -1) {
            throw new IOException("Write error.");
        }
    }


    @Override
    public String getConnectionName() {
        return "serial://" + portName;
    }

    public String getPortName() {
        return portName;
    }

    public void setPortName(String portName) {
        this.portName = portName;
    }

    public int getBaud() {
        return baud;
    }

    public void setBaud(int baud) {
        this.baud = baud;
    }

    public FlowControl getFlowControl() {
        return flowControl;
    }

    public void setFlowControl(FlowControl flowControl) {
        this.flowControl = flowControl;
    }

    public DataBits getDataBits() {
        return dataBits;
    }

    public void setDataBits(DataBits dataBits) {
        this.dataBits = dataBits;
    }

    public StopBits getStopBits() {
        return stopBits;
    }

    public void setStopBits(StopBits stopBits) {
        this.stopBits = stopBits;
    }

    public Parity getParity() {
        return parity;
    }

    public void setParity(Parity parity) {
        this.parity = parity;
    }

    public boolean isSetDtr() {
        return setDtr;
    }

    public void setSetDtr(boolean setDtr) {
        this.setDtr = setDtr;
    }

    public boolean isSetRts() {
        return setRts;
    }

    public void setSetRts(boolean setRts) {
        this.setRts = setRts;
    }
}

