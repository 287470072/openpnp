package org.openpnp.util;

import org.openpnp.model.Serial;
import org.testng.annotations.Test;

import java.net.InetAddress;
import java.sql.Timestamp;

/**
 * 序列号生成、验证入口类
 *
 * @author: yuanzf
 * @date: 2022/3/16 11:37
 */
public class SerialUtilTest {
    /**
     * 测试方法
     * 生成rsa密钥对，需保存密钥对，后边生成序列号和解释序列号需要用到
     */
    @Test
    public void getKeyPairStart() {
        /*提前生成好rsa密钥对,私钥保存至服务端,公钥写入至客户端*/
        RSAUtil rsa = new RSAUtil();
        System.out.println("我是rsa公钥,请将我保存：" + rsa.getPublicKey());
        System.out.println("我是rsa私钥,请将我保存：" + rsa.getPrivateKey());
    }

    /**
     * 测试方法
     * 生成序列号，生成后将序列号提供至客户端进行配置
     */
    @Test
    public void generateStart() {
        String privateKey = "MIICdwIBADANBgkqhkiG9w0BAQEFAASCAmEwggJdAgEAAoGBAI9CogaFgQ/LyMb5D0MBn86iksINYZ1Mi10/37YhNr5V4RIG3VuNmV7wAoeM0zVwhDlrUnXNor+hBjwfjcItwMUIr0jv0hrAfFiN5GXHRgcV+06ojAmVX3A3PpwtqHt+ezlcMpo/xJYn3sluHptjr1fJWo/7OQGVSiPnmRO5o6rRAgMBAAECgYEAgS2tWkIR0YOJBNnaCCqzxijUOsTEK4m09R2+hMVITrKzo4SrH338OR4kucOjQ8G0iO4cSftl8HOrYpV1Rw3ojqPZXBVnmht7Ku0O4bW/aorBjS/T3TaOu5XG/iurSXcQ+VFmVlA3N4SZsZmqtU+/ZidXveJnXBya9GNYe2mZAL0CQQDk4xITvXgG8amclbBCpMWWPD07BRS952QGEGPIlDayn4cs7wNf3hneT8Dyh3VwPK2B+t+4Us/J3hGPVrbGajrvAkEAoDr1QbyjKXU8+Ii5bD6WuwMcdbbmCHW4gRGlhzsR6B0tT8kwkUj7x3Rhuq9NubmkJVMcmZGRBnnN3PL+Ovt2PwJBAOJRB9HO+TxcfWoftF3hEKHhRGX0OzMe9Y7ta8yriH4MMRuj5YFIWemwkEb+24cz6BfqmIVoFJ803cYOZsE/zq8CQBcOJLgewoN2oR1J3xRaNSoXmoK9nH/fIHtB5MV+lGcHu7tdQaXGEKR0dJN+Ifr1YU8VlGnsbie2Yw7F0BsAj9UCQA3MSaW0Vxxz5oehwvynu938LI/EOuo6DP223F5W0+3MX1iET4q6KeUH28Ci/uHsCI3bAnZ8eCnrrJX0UxjN168=";
        /*new一个序列类,构造时传入私钥、MAC地址、有效结束时间*/
        Serial serial = new Serial(privateKey, "2433BF4969E30E55A637F8447F668AE3", "4102415999000");
        /*生成序列号,参数是引用类型，调用生成序列号方法后可直接从对象中获取序列号*/
        SerialUtil.generateSerialNumber(serial);
        /*获取序列号*/
        String serialNumber = serial.getSerialNumber();
        System.out.println("我是生成的序列号,请发送至客户端：" + serialNumber);
    }

    /**
     * 测试方法
     * 校验序列号是否有效
     */
    @Test
    public void explainStart() {
        try {
            String publicKey = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCPQqIGhYEPy8jG+Q9DAZ/OopLCDWGdTItdP9+2ITa+VeESBt1bjZle8AKHjNM1cIQ5a1J1zaK/oQY8H43CLcDFCK9I79IawHxYjeRlx0YHFftOqIwJlV9wNz6cLah7fns5XDKaP8SWJ97Jbh6bY69XyVqP+zkBlUoj55kTuaOq0QIDAQAB";
            /*new一个序列类,构造时传入公钥、序列号*/
            Serial serial = new Serial(publicKey, "355183DF97E7EC4710D831DD596232389CDFFD18A817FD2188E6B897DD0BEED2F06CF131C64539E888D924C1B32A5CC1400169B1CE155DD0DBA1C952C7010843EC6E76836487CBDF8C75A7F422AF8505A6BB1C26B087880287A1202716F88A220E42F6437338919F986CBFFC800E95A3D0850C22F05B770CA79A2B9D8BD55F1B");
            /*解释序列号,参数是引用类型，调用解释序列号方法后可直接从对象中获取MAC地址、有效结束时间*/
            SerialUtil.explainSerialNumber(serial);
            /*获取MAC地址*/
            String mac = serial.getMac();
            /*获取有效结束时间*/
            String effectiveEndTime = serial.getEffectiveEndTime();
            /*获取本机MAC地址*/
            String localMac = MachineCodeUtil.getThisMachineCodeMd5();
            /*获取当前时间戳*/
            long time = new Timestamp(System.currentTimeMillis()).getTime();
            /*判断有效结束时间是否大于当前时间*/
            if (mac.equals(localMac) && Long.parseLong(effectiveEndTime) > time) {
                System.out.println("当前序列号有效，可正常使用系统与访问资源");
            } else {
                System.out.println("当前序列号无效，请向厂商购买新的序列号");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}