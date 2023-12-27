package org.openpnp.util;


import org.openpnp.machine.reference.driver.AbstractReferenceDriver;
import org.openpnp.machine.reference.driver.GcodeDriver;
import org.openpnp.machine.reference.driver.SerialPortCommunications;
import org.openpnp.model.Configuration;
import org.openpnp.model.Serial;
import org.openpnp.spi.Driver;
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
import java.util.Map;
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

    public static void checkSerialFile() throws Exception {
        List<Driver> drivers = Configuration.get().getMachine().getDrivers();
        Map<String, String> portNames = SerialPortCommunications.getPortDescribe();
        for (Driver driver : drivers) {
            if (driver instanceof GcodeDriver) {
                portNames.forEach((key, value) -> {
                    if (key.contains("CH341A")) {
                        try {
                            ((GcodeDriver) driver).connect();
                            ((GcodeDriver) driver).sendCommand("M701 E1 T");
                            String deviceId = ((GcodeDriver) driver).receiveSingleResponse(".*ok.*");
                            deviceId = deviceId.replace("ok!", "");
                            ((GcodeDriver) driver).sendCommand("M701 F1 T");
                            String deviceRes = ((GcodeDriver) driver).receiveSingleResponse(".*ok.*");
                            deviceRes = deviceRes.replace("ok!", "");
                            String privateKey = "MIICdwIBADANBgkqhkiG9w0BAQEFAASCAmEwggJdAgEAAoGBAIZNXJ9ObsxAm6VdGDQN2C32ptSuPeqDZDBcbtJ3KzaHU8/MbrmLxnjTyhQN4rhxQpSW0sspFOeecWoYeXehwXoWfphCHi6Vfyg7RviEsAH1FD5ioNPVeNb49hmlREALH2thMCUlW+Eo1rKtbla55MY9wB156SBhn5NuRWUTeKhnAgMBAAECgYBqk+n04iE7JepeiEo0xOfRUfOCw+OOv0Y6up+XlcpNM4dnWCxmQm32ZNvwnjRVekwD7szJPIjCZhJKx7FdJpiKRAZDxDFQWPW0s3jijz4pX17PBP53pLTEAO8uMKZEIHA2aQ3bF6scdhtoI5fDO4ep8BtTvtXzw8NvI6BarJ90wQJBAOos7dbqDs4ivCz2wH3+fsgM8uXRnOKmrJVicarODPZFHQQnr3PByZFM6461gr2758SXgSn47AIMAHwsLx8xMD0CQQCS0ZuPmpCWTg94m6NzQaydQaltEgRpA4DgJ6Uk2bma4EIIHKP5yqo7UHOGNZXKQLSdcJNc2Jqhihvem8xlKsFzAkBkkEbTNFCHVYNaC90+PjxTzLvC1fF5o/oZbN1DbJlEaQm87w35uA7HxzChaHFs6XTuh+GAFNXFS0IqEQ9rZcRBAkEAgxTBXqUREiD/jx7l/7FS+9P0AH1lkpyeI4NB3nTFUZGHYtavUAWxluNtQRX2dmzu1OH9r5dz92XnHAjdpDVYIQJBAIh4v014nIaHQ+myKZd8p4b2DxJydpGg8HN4pyG6fBG90IJfeJvTzNrDU8S9ifOzO8lIRi5xjfQ7l6PvfioCweU=";
                            String md5 = MD5Utils.mD5(deviceId + privateKey).substring(8, 24);
                            Serial serial = new Serial();
                            if (md5.equals(deviceRes)) {
                                serial.setCertification(true);
                            } else {
                                serial.setCertification(false);
                            }
                            Configuration.get().setSerial(serial);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                });

            }
        }
    }


}
