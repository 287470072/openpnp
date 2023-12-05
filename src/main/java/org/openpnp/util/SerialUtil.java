package org.openpnp.util;


import org.openpnp.model.Serial;
import org.pmw.tinylog.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 序列号工具类
 *
 * @author yuanzf
 */
public class SerialUtil {
    /**
     * 根据私钥、MAC地址、有效期生成序列号
     *
     * @param serial 需有privateKey、mac、effectiveEndTime(13位时间戳)值
     * @return serial 含有privateKey、mac、effectiveEndTime、serialNumber值
     * @throws Exception
     */
    public static Serial generateSerialNumber(Serial serial) {
        RSAUtil rsa = new RSAUtil(null, serial.getPrivateKey());
        String serialNumber = rsa.encrypt(serial.getMac() + "," + serial.getEffectiveEndTime());
        serial.setSerialNumber(serialNumber);
        return serial;

    }

    /**
     * 根据序列号、公钥校验序列号是否有效
     *
     * @param serial 需有publicKey、serialNumber
     * @return serial 含有publicKey、mac、effectiveEndTime、serialNumber值
     * @throws Exception
     */
    public static Serial explainSerialNumber(Serial serial) {
        RSAUtil rsa = new RSAUtil(serial.getPublicKey(), null);
        String[] serialList = rsa.decrypt(serial.getSerialNumber()).split(",");
        serial.setMac(serialList[0]);
        serial.setEffectiveEndTime(serialList[1]);
        return serial;

    }

    public static boolean checkSerialFile() {
        String currentDirectory = System.getProperty("user.dir");
        String filePath = "keys.txt";
        String fullPath = currentDirectory + File.separator + filePath;
        String publicKey = "";
        String serial = "";

        Logger.trace(fullPath);

        // 判断文件是否存在
        if (Files.exists(Path.of(fullPath))) {
            try {
                FileReader reader = new FileReader(fullPath);
                BufferedReader in = new BufferedReader(reader);
                String iniContent;
                Pattern keyValuePattern = Pattern.compile("(.*?)=(.*)");
                // 解析 Ini 文件内容
                while ((iniContent = in.readLine()) != null) {
                    Matcher keyValueMatcher = keyValuePattern.matcher(iniContent);

                    if (keyValueMatcher.matches()) {
                        String key = keyValueMatcher.group(1);
                        String value = keyValueMatcher.group(2);

                        if (key.equals("publicKey")) {
                            publicKey = value;
                        } else if (key.equals("serial")) {
                            serial = value;
                        }
                    }
                }
                if (!publicKey.equals("") && !serial.equals("")) {
                    Serial serialtemp = new Serial(publicKey, serial);
                    explainSerialNumber(serialtemp);
                    /*获取MAC地址*/
                    String mac = serialtemp.getMac();
                    /*获取有效结束时间*/
                    String effectiveEndTime = serialtemp.getEffectiveEndTime();
                    /*获取本机MAC地址*/
                    String localMac = MachineCodeUtil.getThisMachineCodeMd5();
                    /*获取当前时间戳*/
                    long time = new Timestamp(System.currentTimeMillis()).getTime();
                    /*判断有效结束时间是否大于当前时间*/
                    if (mac.equals(localMac) && Long.parseLong(effectiveEndTime) > time) {
                        return true;
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            return false;
        }
    }
}
