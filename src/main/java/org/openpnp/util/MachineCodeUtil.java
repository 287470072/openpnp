package org.openpnp.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

public class MachineCodeUtil {
    public static final String LINUX_OS_NAME = "LINUX";
    public static final String SYSTEM_PROPERTY_OS_NAME = "os.name";

    public static void main(String[] args) {
        System.out.println(getThisMachineCode());
        System.out.println(getThisMachineCodeMd5());
    }

    /**
     * 获取机器唯一识别码（CPU ID + BIOS UUID）
     *
     * @return 机器唯一识别码
     */
    public static String getThisMachineCode() {
        try {
            return getCpuId() + getBiosUuid();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * 获取机器码进行MD5摘要后的字符串
     *
     * @return
     */
    public static String getThisMachineCodeMd5() {
        try {
            String thisMachineCode = getThisMachineCode();
            String md5Upper = MD5Utils.mD5Upper(thisMachineCode);
            return md5Upper;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * 获取当前系统CPU序列，可区分linux系统和windows系统
     */
    public static String getCpuId() throws IOException {
        String cpuId;
        // 获取当前操作系统名称
        String os = System.getProperty(SYSTEM_PROPERTY_OS_NAME);
        os = os.toUpperCase();

        if (LINUX_OS_NAME.equals(os)) {
            cpuId = getLinuxDmidecodeInfo("dmidecode -t processor | grep 'ID'", "ID", ":");
        } else {
            cpuId = getWindowsCpuId();
        }
        return cpuId.toUpperCase().replace(" ", "");
    }

    /**
     * 获取linux系统
     * dmidecode
     * 命令的信息
     */
    public static String getLinuxDmidecodeInfo(String cmd, String record, String symbol) throws IOException {
        String execResult = executeLinuxCmd(cmd);
        String[] infos = execResult.split("\n");
        for (String info : infos) {
            info = info.trim();
            if (info.contains(record)) {
                info.replace(" ", "");
                String[] sn = info.split(symbol);
                return sn[1];
            }
        }
        return null;
    }


    /**
     * 执行Linux 命令
     *
     * @param cmd Linux 命令
     * @return 命令结果信息
     * @throws IOException 执行命令期间发生的IO异常
     */
    public static String executeLinuxCmd(String cmd) throws IOException {
        Runtime run = Runtime.getRuntime();
        Process process;
        process = run.exec(cmd);
        InputStream processInputStream = process.getInputStream();
        StringBuilder stringBuilder = new StringBuilder();
        byte[] b = new byte[8192];
        for (int n; (n = processInputStream.read(b)) != -1; ) {
            stringBuilder.append(new String(b, 0, n));
        }
        processInputStream.close();
        process.destroy();
        return stringBuilder.toString();
    }

    /**
     * 获取windows系统CPU序列
     */
    public static String getWindowsCpuId() throws IOException {
        Process process = Runtime.getRuntime().exec(
                new String[]{"wmic", "cpu", "get", "ProcessorId"});
        process.getOutputStream().close();
        Scanner sc = new Scanner(process.getInputStream());
        sc.next();
        String serial = sc.next();
        return serial;
    }

    /**
     * 获取 BIOS UUID
     *
     * @return BIOS UUID
     * @throws IOException 获取BIOS UUID期间的IO异常
     */
    public static String getBiosUuid() throws IOException {
        String cpuId;
        // 获取当前操作系统名称
        String os = System.getProperty("os.name");
        os = os.toUpperCase();

        if ("LINUX".equals(os)) {
            cpuId = getLinuxDmidecodeInfo("dmidecode -t system | grep 'UUID'", "UUID", ":");
        } else {
            cpuId = getWindowsBiosUUID();
        }
        return cpuId.toUpperCase().replace(" ", "");
    }

    /**
     * 获取windows系统 bios uuid
     *
     * @return
     * @throws IOException
     */
    public static String getWindowsBiosUUID() throws IOException {
        Process process = Runtime.getRuntime().exec(
                new String[]{"wmic", "path", "win32_computersystemproduct", "get", "uuid"});
        process.getOutputStream().close();
        Scanner sc = new Scanner(process.getInputStream());
        sc.next();
        String serial = sc.next();
        return serial;
    }
}
