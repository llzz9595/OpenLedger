/*
 *   Copyright (C) @2021 Webank Group Holding Limited
 *   <p>
 *   Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at
 *  <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 *   <p>
 *   Unless required by applicable law or agreed to in writing, software distributed under the License
 *   is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 *  or implied. See the License for the specific language governing permissions and limitations under
 *  he License.
 *
 */

package com.webank.openledger.core.asset.fungible;


import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.util.List;

import com.webank.openledger.contracts.AuthCenter;
import com.webank.openledger.contracts.FungibleAsset;
import com.webank.openledger.core.AccountImplTest;
import com.webank.openledger.core.Blockchain;
import com.webank.openledger.core.asset.fungible.entity.AssetEntity;
import com.webank.openledger.core.asset.fungible.entity.Condition;
import com.webank.openledger.core.asset.fungible.entity.RecordEntity;
import com.webank.openledger.core.asset.fungible.entity.TransferResult;
import com.webank.openledger.core.auth.AuthCenterService;
import com.webank.openledger.core.constant.ErrorCode;
import com.webank.openledger.core.exception.OpenLedgerBaseException;
import com.webank.openledger.core.response.DataToolUtils;
import com.webank.openledger.core.response.ResponseData;
import com.webank.openledger.utils.OpenLedgerUtils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.fisco.bcos.sdk.crypto.CryptoSuite;
import org.fisco.bcos.sdk.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.crypto.signature.ECDSASignatureResult;
import org.fisco.bcos.sdk.model.CryptoType;
import org.fisco.bcos.sdk.model.TransactionReceipt;
import org.fisco.bcos.sdk.transaction.model.exception.ContractException;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Slf4j
public class FungibleAssetServiceTest {
    private static final String AUTH_ADDRESS = "0xfb1f7fc9b23e8c86c3200610c56bb57576a1f6a3";
    private static final String ORG_ADDRESS = "0x81cc905d231db4dbb9b5ffe6dd9158f61d3c0f3e";
    private String contractAddress = "0x9857ea5d68fb1a7888d24c27e66f65b0cbded57b";
    Blockchain blockchain;
    CryptoSuite ecdsaCryptoSuite = new CryptoSuite(CryptoType.ECDSA_TYPE);
    CryptoKeyPair admin;
    CryptoKeyPair operator;
    CryptoKeyPair owner;
    CryptoKeyPair user;
    private FungibleAssetService fungibleAssetService;
    private AuthCenterService<AuthCenter> authCenterSDK;

    @Before
    public void init() {
        log.info("Begin Test-----------------");
        blockchain = new Blockchain("application.properties");
        String pemFile = AccountImplTest.class.getClassLoader().getResource("conf/test.pem").getPath();
        ecdsaCryptoSuite.loadAccount("pem", pemFile, "");
        admin = ecdsaCryptoSuite.getCryptoKeyPair();
        log.info(admin.getAddress());
        pemFile = AccountImplTest.class.getClassLoader().getResource("conf/test2.pem").getPath();
        ecdsaCryptoSuite.loadAccount("pem", pemFile, "");
        operator = ecdsaCryptoSuite.getCryptoKeyPair();
        log.info(operator.getAddress());
        pemFile = AccountImplTest.class.getClassLoader().getResource("conf/test3.pem").getPath();
        ecdsaCryptoSuite.loadAccount("pem", pemFile, "");
        owner = ecdsaCryptoSuite.getCryptoKeyPair();
        log.info(owner.getAddress());

        pemFile = AccountImplTest.class.getClassLoader().getResource("conf/test4.pem").getPath();
        ecdsaCryptoSuite.loadAccount("pem", pemFile, "");
        user = ecdsaCryptoSuite.getCryptoKeyPair();
        log.info(user.getAddress());


        if (StringUtils.isNotBlank(contractAddress)) {
            this.fungibleAssetService = new FungibleAssetService(blockchain, contractAddress);
            this.authCenterSDK = new AuthCenterService<>(blockchain, AUTH_ADDRESS);
        }
    }

    @Test
    public void deploy() throws OpenLedgerBaseException, ContractException {
        String tableName = "myasset13";
        FungibleAsset asset = FungibleAsset.deploy(blockchain.getClient(Blockchain.DEFAULT_LEDGERID), blockchain.getProjectAccount().getKeyPair(), tableName, AUTH_ADDRESS, ORG_ADDRESS);
        TransactionReceipt tr = asset.getDeployReceipt();

        log.info(DataToolUtils.decodeOutputReturnString0x16(tr.getOutput()));
        log.info(asset.getAddress());
        contractAddress = asset.getAddress();

    }


    @Test
    public void getAsset() {
        assertNotNull(fungibleAssetService.getAsset());
        assertNotNull(fungibleAssetService.getIdentity());
    }

    @Test
    public void getAssetInfo() throws OpenLedgerBaseException {
        BigInteger assetPrice = BigInteger.valueOf(100);
        BigInteger assetRate = BigInteger.valueOf(200);
        BigInteger nonce = authCenterSDK.getNonceFromAccount(admin.getAddress()).getResult();
        byte[] messagePrice = OpenLedgerUtils.computeKeccak256Hash(OpenLedgerUtils.concatByte(OpenLedgerUtils.getBytes32(assetPrice.toByteArray()), OpenLedgerUtils.getBytes32(nonce.toByteArray())));

        ResponseData<BigInteger> responseRPriceData = fungibleAssetService.setPrice(assetPrice, messagePrice, OpenLedgerUtils.sign(admin, messagePrice));
        log.info(responseRPriceData.getErrMsg());
        assertEquals(ErrorCode.SUCCESS.getCode(), responseRPriceData.getErrorCode().intValue());

        nonce = authCenterSDK.getNonceFromAccount(admin.getAddress()).getResult();
        byte[] messageRate = OpenLedgerUtils.computeKeccak256Hash(OpenLedgerUtils.concatByte(OpenLedgerUtils.getBytes32(assetRate.toByteArray()), OpenLedgerUtils.getBytes32(nonce.toByteArray())));

        ResponseData<BigInteger> responseRateData = fungibleAssetService.setRate(assetRate, messageRate, OpenLedgerUtils.sign(admin, messageRate));
        assertEquals(ErrorCode.SUCCESS.getCode(), responseRateData.getErrorCode().intValue());

        AssetEntity assetEntity = fungibleAssetService.getAssetInfo();
        assertNotNull(assetEntity);
        assertEquals(contractAddress, assetEntity.getAddress());
        assertEquals(assetPrice, assetEntity.getPrice());
        assertEquals(assetRate, assetEntity.getRate());
    }

    @Test
    public void openAccount() throws OpenLedgerBaseException, ContractException {
        // 交易参数
        String fromAddress = admin.getAddress();

        BigInteger nonce = authCenterSDK.getNonceFromAccount(admin.getAddress()).getResult();
        byte[] result = new byte[0];
        result = OpenLedgerUtils.concatByte(result, OpenLedgerUtils.convertStringToAddressByte(fromAddress));
        byte[] messageOpenAccount = StandardAssetService.computeOpenAccountMsg(fromAddress, nonce);

        ResponseData<Boolean> responseData = fungibleAssetService.openAccount(fromAddress, messageOpenAccount, OpenLedgerUtils.sign(admin, messageOpenAccount));
        log.info(responseData.getErrMsg());
        assertTrue(responseData.getResult());


    }

    @Test
    public void deposit() throws OpenLedgerBaseException, UnsupportedEncodingException {
        // 交易参数
        String account = admin.getAddress();
        String operatorAddress = admin.getAddress();
        BigInteger amount = BigInteger.valueOf(100);
        String detail = "test";
        //交易序列号 从authcenter获取
        BigInteger nonce = authCenterSDK.getNonceFromAccount(admin.getAddress()).getResult();
        List<String> addressList = StandardAssetService.genAddress(null, account, operatorAddress, contractAddress, null);
        byte[] message = StandardAssetService.computeTxMsg(addressList, amount, StandardAssetService.genType(1), StandardAssetService.genDetail(detail, null), nonce);
        ECDSASignatureResult sign = OpenLedgerUtils.sign(admin, message);

        ResponseData<TransferResult> responseData = fungibleAssetService.deposit(operatorAddress, account, amount, 1, detail, message, sign);
        assertNotNull(fungibleAssetService.getAsset());
        List<FungibleAsset.InsertResultEventResponse>  response =fungibleAssetService.getAsset().getInsertResultEvents(responseData.getTransactionInfo().getTransactionReceipt());
        log.info(response.get(0).from);
        log.info(response.get(0).to);
        log.info(response.get(0).seqNo.toString());
        log.info(response.get(0).termNo.toString());
        log.info(response.get(0).amount.toString());

        log.info(responseData.getErrMsg());
        assertTrue(responseData.getResult() != null && responseData.getResult().getIsSuccees());
        log.info(responseData.getResult().toString());
    }

    @Test
    public void withdrawal() throws OpenLedgerBaseException, UnsupportedEncodingException {
        // 交易参数
        String account = admin.getAddress();
        String operatorAddress = admin.getAddress();
        BigInteger amount = BigInteger.valueOf(1);
        String detail = "test";
        //交易序列号 从authcenter获取

        BigInteger nonce = authCenterSDK.getNonceFromAccount(operatorAddress).getResult();
        List<String> addressList = StandardAssetService.genAddress(account, null, operatorAddress, contractAddress, null);

        byte[] message = StandardAssetService.computeTxMsg(addressList, amount, StandardAssetService.genType(2), StandardAssetService.genDetail(detail, null), nonce);
        ECDSASignatureResult sign = OpenLedgerUtils.sign(admin, message);

        ResponseData<TransferResult> responseData = fungibleAssetService.withdrawal(operatorAddress, account, amount, 2, detail, message, sign);
        List<FungibleAsset.InsertResultEventResponse>  response =fungibleAssetService.getAsset().getInsertResultEvents(responseData.getTransactionInfo().getTransactionReceipt());
        log.info(response.get(0).from);
        log.info(response.get(0).to);
        log.info(response.get(0).seqNo.toString());
        log.info(response.get(0).termNo.toString());
        log.info(response.get(0).amount.toString());
        log.info(responseData.getErrMsg());
        assertTrue(responseData.getResult() != null && responseData.getResult().getIsSuccees());
        log.info(responseData.getResult().toString());
    }

    @Test
    public void getBalance() throws OpenLedgerBaseException {
        // 交易参数
        String fromAddress = user.getAddress();
        BigInteger nonce = authCenterSDK.getNonceFromAccount(user.getAddress()).getResult();
        System.out.println(fungibleAssetService.getBalance(fromAddress, OpenLedgerUtils.computeKeccak256HashFromBigInteger(nonce), OpenLedgerUtils.sign(user, OpenLedgerUtils.computeKeccak256HashFromBigInteger(nonce))));
    }


    @Test
    public void transfer() throws OpenLedgerBaseException, UnsupportedEncodingException {
        log.info(admin.getAddress());
        // 交易参数
        String fromAddress = user.getAddress();
        String toAddress = admin.getAddress();
        String operatorAddress = user.getAddress();
        BigInteger amount = BigInteger.valueOf(10);
        String detail = "test";
        //交易序列号 从authcenter获取
        BigInteger nonce = authCenterSDK.getNonceFromAccount(operatorAddress).getResult();
        byte[] message = StandardAssetService.computeTxMsg(StandardAssetService.genAddress(fromAddress, toAddress, operatorAddress, contractAddress, null), amount, StandardAssetService.genType(3), StandardAssetService.genDetail(detail, null), nonce);
        ResponseData<TransferResult> responseData = fungibleAssetService.transfer(operatorAddress, fromAddress, toAddress, amount, 3, detail, message, OpenLedgerUtils.sign(user, message));
        log.info(responseData.getErrMsg());
        assertTrue(responseData.getResult() != null && responseData.getResult().getIsSuccees());
        log.info(responseData.getResult().toString());
        List<FungibleAsset.InsertResultEventResponse>  response =fungibleAssetService.getAsset().getInsertResultEvents(responseData.getTransactionInfo().getTransactionReceipt());
        log.info(response.get(0).from);
        log.info(response.get(0).to);
        log.info(response.get(0).seqNo.toString());
        log.info(response.get(0).termNo.toString());
        log.info(response.get(0).amount.toString());

    }

    @Test
    public void getHolders() {
        try {
            BigInteger nonce = authCenterSDK.getNonceFromAccount(admin.getAddress()).getResult();
            byte[] message = OpenLedgerUtils.computeKeccak256HashFromBigInteger(nonce);
            System.out.println(fungibleAssetService.getHolders(message, OpenLedgerUtils.sign(admin, message)));
        } catch (OpenLedgerBaseException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void getTotalBalance() throws OpenLedgerBaseException {
        BigInteger nonce = authCenterSDK.getNonceFromAccount(admin.getAddress()).getResult();
        byte[] message = OpenLedgerUtils.computeKeccak256HashFromBigInteger(nonce);
        System.out.println(fungibleAssetService.getTotalBalance(message, OpenLedgerUtils.sign(admin, message)));
    }

    @Test
    public void addBook() throws OpenLedgerBaseException {
        BigInteger nonce = authCenterSDK.getNonceFromAccount(admin.getAddress()).getResult();
        byte[] message = OpenLedgerUtils.computeKeccak256HashFromBigInteger(nonce);
        // 交易参数
        ResponseData<BigInteger> responseData = fungibleAssetService.addBook(message, OpenLedgerUtils.sign(admin, message));
        assertTrue(ErrorCode.SUCCESS.getCode() == responseData.getErrorCode().intValue());
        log.info("booknum:" + responseData.getResult());
    }


    @Test
    public void query() throws Exception {
        BigInteger rightTermNo = BigInteger.valueOf(0);
        BigInteger rightSeq = BigInteger.valueOf(28);

        // 交易参数
        String operatorAddress = user.getAddress();
        BigInteger nonce = authCenterSDK.getNonceFromAccount(admin.getAddress()).getResult();
        byte[] message = OpenLedgerUtils.computeKeccak256HashFromBigInteger(nonce);
        ECDSASignatureResult rs = OpenLedgerUtils.sign(admin, message);
        String account1 = admin.getAddress();
        String account2 = user.getAddress();

        Condition condition1 = new Condition(rightTermNo, rightSeq, null, account1);
        List<RecordEntity> recordEntities = fungibleAssetService.query(condition1, message, rs);
        assertNotNull(recordEntities);
        assertTrue(recordEntities.size() == 1);

        Condition condition2 = new Condition(BigInteger.valueOf(0), rightSeq, account1, account2);
        recordEntities = fungibleAssetService.query(condition2, message, rs);
        assertNotNull(recordEntities);
        recordEntities.stream().forEach(item -> System.out.println(item));
        System.out.println("=====================================");
        assertTrue(recordEntities.size() == 1);

        Condition condition3 = new Condition(rightTermNo, BigInteger.valueOf(0), account1, account2);
        recordEntities = fungibleAssetService.query(condition3, message, rs);
        assertNotNull(recordEntities);
        recordEntities.stream().forEach(item -> System.out.println(item));
        System.out.println("=====================================");
        assertTrue(recordEntities.size() >= 1);

        Condition condition4 = new Condition(BigInteger.valueOf(0), BigInteger.valueOf(0), account1, account2);
        recordEntities = fungibleAssetService.query(condition4, message, rs);
        assertNotNull(recordEntities);
        recordEntities.stream().forEach(item -> System.out.println(item));
        System.out.println("=====================================");
        assertTrue(recordEntities.size() >= 1);

        Condition condition5 = new Condition(BigInteger.valueOf(0), BigInteger.valueOf(0), account1, null);
        recordEntities = fungibleAssetService.query(condition5, message, rs);
        assertNotNull(recordEntities);
        recordEntities.stream().forEach(item -> System.out.println(item));
        System.out.println("=====================================");
        assertTrue(recordEntities.size() >= 1);

        Condition condition7 = new Condition(BigInteger.valueOf(3), rightSeq, account1, account2);
        recordEntities = fungibleAssetService.query(condition7, message, rs);
        assertTrue(recordEntities == null || recordEntities.size() == 0);

        Condition condition8 = new Condition(rightTermNo, BigInteger.valueOf(363), account1, account2);
        recordEntities = fungibleAssetService.query(condition8, message, rs);
        assertTrue(recordEntities == null || recordEntities.size() == 0);


        // 只能查詢自己的賬本 即from/to 是查詢者
        nonce = authCenterSDK.getNonceFromAccount(user.getAddress()).getResult();
        message = OpenLedgerUtils.computeKeccak256HashFromBigInteger(nonce);
        rs = OpenLedgerUtils.sign(user, message);
        Condition condition6 = new Condition(BigInteger.valueOf(0), BigInteger.valueOf(0), null, account2);
        recordEntities = fungibleAssetService.query(condition6, message, rs);
        assertNotNull(recordEntities);
        recordEntities.stream().forEach(item -> System.out.println(item));
        System.out.println("=====================================");
        assertTrue(recordEntities.size() >= 1);

        Condition condition9 = new Condition(rightTermNo, rightSeq, account2, account2);
        recordEntities = fungibleAssetService.query(condition9, message, rs);
        assertTrue(recordEntities == null || recordEntities.size() == 0);


    }

    @Test
    public void queryBookByAdmin() throws Exception {
        BigInteger rightTermNo = BigInteger.valueOf(0);
        BigInteger rightSeq = BigInteger.valueOf(27);
        // 交易参数
        String operatorAddress = user.getAddress();
        BigInteger nonce = authCenterSDK.getNonceFromAccount(admin.getAddress()).getResult();
        byte[] message = OpenLedgerUtils.computeKeccak256HashFromBigInteger(nonce);
        ECDSASignatureResult rs = OpenLedgerUtils.sign(admin, message);
        //组织管理者运行查询全部
        Condition condition10 = new Condition(rightTermNo, rightSeq, null, null);
        List<RecordEntity> recordEntities = fungibleAssetService.query(condition10, message, rs);
        assertNotNull(recordEntities);
        recordEntities.stream().forEach(item -> System.out.println(item));
        System.out.println("=====================================");
        assertTrue(recordEntities.size() >= 1);

    }

    @Test
    public void testCreateKeyPair() {
        admin = ecdsaCryptoSuite.createKeyPair();
        System.out.println(admin.getHexPrivateKey());
        System.out.println(admin.getAddress());

        admin.storeKeyPairWithPem("src/test/resources/conf/test4.pem");
    }



}