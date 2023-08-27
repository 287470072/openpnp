package org.openpnp.util;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.asymmetric.KeyType;
import org.apache.commons.codec.binary.Base64;
import java.security.KeyPair;

/**
 * 〈Function overview〉<br>
 *
 * @className: RSA
 * @package: com.soft.team.base.encryption
 * @author: yuanzf
 * @date: 2022/3/16 15:02
 */

public class RSAUtil {
    private String publicKey;
    private String privateKey;

    /*有参构造方法，加解密时使用。公钥加密需用私钥解密，私钥加密需用公钥解密*/
    public RSAUtil(String publicKey,String privateKey){
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }

    /*无参构造方法，新建密钥对时使用*/
    public RSAUtil(){
        KeyPair pair = SecureUtil.generateKeyPair("RSA");
        publicKey = new String(Base64.encodeBase64(pair.getPublic().getEncoded()));
        privateKey= new String(Base64.encodeBase64((pair.getPrivate().getEncoded())));
    }

    /**
     *
     * @param str 加密前数据
     * @return 返回加密后数据
     */
    public String encrypt(String str){
        return SecureUtil.rsa(privateKey,publicKey).encryptBcd(str, privateKey!=null?KeyType.PrivateKey:KeyType.PublicKey);
    }

    /**
     *
     * @param str 加密后的数据
     * @return 返回解密后数据
     */
    public String decrypt (String str){
        return SecureUtil.rsa(privateKey,publicKey).decryptStrFromBcd(str, privateKey!=null?KeyType.PrivateKey:KeyType.PublicKey);
    }

    public String getPublicKey() {
        return publicKey;
    }
    public String getPrivateKey() {
        return privateKey;
    }
}
