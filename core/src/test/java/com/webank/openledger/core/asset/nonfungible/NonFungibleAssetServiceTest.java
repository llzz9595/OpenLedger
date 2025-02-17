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

package com.webank.openledger.core.asset.nonfungible;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.webank.openledger.contracts.AuthCenter;
import com.webank.openledger.contracts.NonFungibleAsset;
import com.webank.openledger.core.AccountImplTest;
import com.webank.openledger.core.Blockchain;
import com.webank.openledger.core.asset.nonfungible.entity.IssueNoteResult;
import com.webank.openledger.core.asset.nonfungible.entity.IssueOption;
import com.webank.openledger.core.asset.nonfungible.entity.IssueOptionBuilder;
import com.webank.openledger.core.asset.nonfungible.entity.NonFungibleAssetRecord;
import com.webank.openledger.core.asset.nonfungible.entity.NonFungibleCondition;
import com.webank.openledger.core.asset.nonfungible.entity.Note;
import com.webank.openledger.core.asset.nonfungible.entity.TransferNoteResult;
import com.webank.openledger.core.auth.AuthCenterService;
import com.webank.openledger.core.common.ValueModel;
import com.webank.openledger.core.constant.ErrorCode;
import com.webank.openledger.core.exception.OpenLedgerBaseException;
import com.webank.openledger.core.response.DataToolUtils;
import com.webank.openledger.core.response.ResponseData;
import com.webank.openledger.utils.OpenLedgerUtils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.fisco.bcos.sdk.crypto.CryptoSuite;
import org.fisco.bcos.sdk.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.crypto.signature.ECDSASignatureResult;
import org.fisco.bcos.sdk.model.CryptoType;
import org.fisco.bcos.sdk.transaction.model.exception.ContractException;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Slf4j
public class NonFungibleAssetServiceTest {
    private static final String AUTH_ADDRESS = "0x1254e601ecde8bad5372ba188b26cb2052b56cde";
    private static final String ORG_ADDRESS = "0x5106c2658d88a4e21137e259cb684b4d3741b65e";

    private String contractAddress = "0xa54edc7f3c9543b0d07fddc6690a2e100f86112f";
    Blockchain blockchain;
    CryptoSuite ecdsaCryptoSuite = new CryptoSuite(CryptoType.ECDSA_TYPE);
    CryptoKeyPair admin;
    CryptoKeyPair operator;
    CryptoKeyPair owner;
    CryptoKeyPair user;
    private NonFungibleAssetService nonFungibleAssetService;
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
            this.nonFungibleAssetService = new NonFungibleAssetService(blockchain, contractAddress);
            this.authCenterSDK = new AuthCenterService<>(blockchain, AUTH_ADDRESS);
        }
    }

    @Test
    public void getAssetInfo() throws OpenLedgerBaseException, ContractException {
        BigInteger assetPrice = BigInteger.valueOf(100);
        BigInteger nonce = authCenterSDK.getNonceFromAccount(admin.getAddress()).getResult();
        byte[] messagePrice = OpenLedgerUtils.computeKeccak256Hash(OpenLedgerUtils.concatByte(OpenLedgerUtils.getBytes32(assetPrice.toByteArray()), OpenLedgerUtils.getBytes32(nonce.toByteArray())));

        ResponseData<BigInteger> responseRPriceData = nonFungibleAssetService.setPrice(assetPrice, messagePrice, OpenLedgerUtils.sign(admin, messagePrice));
        log.info(responseRPriceData.getErrMsg());
        assertEquals(ErrorCode.SUCCESS.getCode(), responseRPriceData.getErrorCode().intValue());
    }

    @Test
    public void testOpenAccount() throws OpenLedgerBaseException {
        BigInteger nonce = authCenterSDK.getNonceFromAccount(admin.getAddress()).getResult();
        byte[] args = OpenLedgerUtils.concatByte(OpenLedgerUtils.convertStringToAddressByte(admin.getAddress()), OpenLedgerUtils.getBytes32(nonce.toByteArray()));
        byte[] message = OpenLedgerUtils.computeKeccak256Hash(args);
        ECDSASignatureResult sign = OpenLedgerUtils.sign(admin, message);

        ResponseData<Boolean> response = nonFungibleAssetService.openAccount(admin.getAddress(), message, sign);
        log.info(response.getErrMsg());
        assertTrue(response.getResult());
    }

    @Test
    public void testIssue() throws Exception {
        BigInteger num = BigInteger.valueOf(200);
        BigInteger notePreFix = BigInteger.valueOf(2021);
        BigInteger noteNoSize = BigInteger.valueOf(3);
        Date effectiveDate = DateUtils.addYears(new Date(), 1);
        Date expireDate = DateUtils.addYears(new Date(), 2);
        IssueOption issueOption = IssueOptionBuilder.builder()
                .withAmount(num)
                .withNoteNoPrefix(notePreFix)
                .withNoteNoSize(noteNoSize)
                .withIssuer(admin.getAddress())
                .withOperator(admin.getAddress())
                .withDesc("desc")
//                .withEffectiveDate(effectiveDate)
                .withExpirationDate(expireDate).build();

        //交易序列号 从authcenter获取
        BigInteger nonce = authCenterSDK.getNonceFromAccount(admin.getAddress()).getResult();
        byte[] message = NonFungibleAssetService.computeIssueMsg(contractAddress, issueOption, nonce);
        ECDSASignatureResult sign = OpenLedgerUtils.sign(admin, message);
        ResponseData<List<IssueNoteResult>> response = nonFungibleAssetService.issue(issueOption, message, sign);
        log.info(response.getErrMsg());
        log.info(response.getResult().toString());

        assertTrue(response.getErrorCode().equals(ErrorCode.SUCCESS.getCode()));
        log.info("totalNoteSize:" + nonFungibleAssetService.getAsset().getTotalNoteSize());
        log.info("totalNoteSize:" + nonFungibleAssetService.getAsset().getAccountNoteSize(admin.getAddress(), OpenLedgerUtils.convertSignToByte(message, sign)));
    }

    @Test
    public void testGetNoteDetail() throws OpenLedgerBaseException {
        String noteNo = "2021004";
        BigInteger nonce = authCenterSDK.getNonceFromAccount(admin.getAddress()).getResult();
        byte[] message = OpenLedgerUtils.computeKeccak256Hash(OpenLedgerUtils.getBytes32(nonce.toByteArray()));
        ECDSASignatureResult sign = OpenLedgerUtils.sign(admin, message);

        Note response = nonFungibleAssetService.getNoteDetail(new BigInteger(noteNo), admin.getAddress(), message, sign);
        log.info(response.toString());
        assertEquals(new BigInteger(noteNo).intValue(), response.getNoteNo().intValue());
    }

    @Test
    public void testTransfer() throws OpenLedgerBaseException, UnsupportedEncodingException {
        BigInteger noteNo1 = new BigInteger("20220004");
        BigInteger noteNo2 = new BigInteger("20220005");
        List<BigInteger> noteNos = new ArrayList<>();
        noteNos.add(noteNo1);
        noteNos.add(noteNo2);
        BigInteger nonce = authCenterSDK.getNonceFromAccount(admin.getAddress()).getResult();
        byte[] message = NonFungibleAssetService.computeTransferMsg(contractAddress, admin.getAddress(), admin.getAddress(), user.getAddress(), noteNos, "desc", nonce);
        ECDSASignatureResult sign = OpenLedgerUtils.sign(admin, message);
        ResponseData<List<TransferNoteResult>> response = nonFungibleAssetService.transfer(admin.getAddress(), admin.getAddress(), user.getAddress(), noteNos, "desc", message, sign);
        log.info(response.getErrMsg());
        log.info(response.getResult().toString());
        assertTrue(response.getErrorCode().equals(ErrorCode.SUCCESS.getCode()));
    }

    @Test
    public void testGetAccountNotes() throws OpenLedgerBaseException {
        BigInteger nonce = authCenterSDK.getNonceFromAccount(user.getAddress()).getResult();
        byte[] message = OpenLedgerUtils.computeKeccak256Hash(OpenLedgerUtils.getBytes32(nonce.toByteArray()));
        ECDSASignatureResult sign = OpenLedgerUtils.sign(user, message);

        List<BigInteger> response = nonFungibleAssetService.getAccountNotes(user.getAddress(), BigInteger.valueOf(0), BigInteger.valueOf(10), message, sign);
        log.info(response.toString());
    }

    @Test
    public void testUpdateNoteNo() throws OpenLedgerBaseException {
        BigInteger noteNo1 = new BigInteger("20210004");
        BigInteger noteNo2 = new BigInteger("30210023");
        BigInteger nonce = authCenterSDK.getNonceFromAccount(admin.getAddress()).getResult();
        byte[] message = OpenLedgerUtils.computeKeccak256Hash(OpenLedgerUtils.concatByte(OpenLedgerUtils.getBytes32(noteNo1.toByteArray()), OpenLedgerUtils.getBytes32(noteNo2.toByteArray()), OpenLedgerUtils.convertStringToAddressByte(admin.getAddress()), OpenLedgerUtils.getBytes32(nonce.toByteArray())));

        ECDSASignatureResult sign = OpenLedgerUtils.sign(admin, message);

        ResponseData<Boolean> response = nonFungibleAssetService.updateNoteNo(noteNo1, noteNo2, admin.getAddress(), message, sign);
        log.info(response.getErrMsg());
        assertTrue(response.getResult());
    }

    @Test
    public void testUpdateNoteItems() throws OpenLedgerBaseException {
        BigInteger noteNo = new BigInteger("20210005");
        HashMap<String, Object> items = new HashMap<>();
        items.put("hello", "world");
        BigInteger nonce = authCenterSDK.getNonceFromAccount(admin.getAddress()).getResult();
        byte[] args = OpenLedgerUtils.getBytes32(noteNo.toByteArray());
        ValueModel vm = null;
        for (Map.Entry<String, Object> entry : items.entrySet()) {
            String mapKey = entry.getKey();
            Object mapValue = entry.getValue();
            vm = new ValueModel(mapValue);
            args = OpenLedgerUtils.concatByte(args, mapKey.getBytes(Charset.defaultCharset()), ValueModel.getByteVal(vm));
        }

        byte[] message = OpenLedgerUtils.computeKeccak256Hash(OpenLedgerUtils.concatByte(args, OpenLedgerUtils.convertStringToAddressByte(admin.getAddress()), OpenLedgerUtils.getBytes32(nonce.toByteArray())));
        ECDSASignatureResult sign = OpenLedgerUtils.sign(admin, message);
        ResponseData<Map<String, Object>> response = nonFungibleAssetService.updateNoteProperties(admin.getAddress(), noteNo, items, message, sign);
        log.info(response.getErrMsg());
        log.info(response.getResult().toString());
        assertTrue(response.getResult().containsKey("hello"));
        Object value = response.getResult().get("hello");
        assertEquals(value, "world");
    }

    @Test
    public void testGetNoteItems() throws OpenLedgerBaseException {
        BigInteger nonce = authCenterSDK.getNonceFromAccount(admin.getAddress()).getResult();
        BigInteger noteNo = new BigInteger("20210005");
        byte[] message = OpenLedgerUtils.computeKeccak256Hash(OpenLedgerUtils.getBytes32(nonce.toByteArray()));
        ECDSASignatureResult sign = OpenLedgerUtils.sign(admin, message);

        Map<String, Object> response = nonFungibleAssetService.getNoteProperties(noteNo, admin.getAddress(), message, sign);
        log.info(response.toString());
        Object value = response.get("hello");
        assertEquals(value, "world");
    }

    @Test
    public void testUpdateNoteBatch() throws OpenLedgerBaseException {
        BigInteger batchNo = BigInteger.valueOf(1);
        BigInteger nonce = authCenterSDK.getNonceFromAccount(admin.getAddress()).getResult();
        Date expireDate = DateUtils.addYears(new Date(), 2);
        byte[] args = OpenLedgerUtils.concatByte(OpenLedgerUtils.getBytes32(batchNo.toByteArray()),
                OpenLedgerUtils.getBytes32(BigInteger.valueOf(expireDate.getTime()).toByteArray()),
                OpenLedgerUtils.convertStringToAddressByte(admin.getAddress()),
                OpenLedgerUtils.getBytes32(nonce.toByteArray())
        );
        byte[] message = OpenLedgerUtils.computeKeccak256Hash(args);
        ECDSASignatureResult sign = OpenLedgerUtils.sign(admin, message);

        ResponseData<Boolean> responseData = nonFungibleAssetService.updateExpirationDate(batchNo, expireDate, admin.getAddress(), message, sign);
        log.info(responseData.getErrMsg());
        assertTrue(responseData.getResult());
        Date effectiveDate = DateUtils.addYears(new Date(), 1);
        args = OpenLedgerUtils.concatByte(OpenLedgerUtils.getBytes32(batchNo.toByteArray()),
                OpenLedgerUtils.getBytes32(BigInteger.valueOf(effectiveDate.getTime()).toByteArray()),
                OpenLedgerUtils.convertStringToAddressByte(admin.getAddress()),
                OpenLedgerUtils.getBytes32(nonce.toByteArray())
        );
        message = OpenLedgerUtils.computeKeccak256Hash(args);
        sign = OpenLedgerUtils.sign(admin, message);
        responseData = nonFungibleAssetService.updateEffectiveDate(batchNo, effectiveDate, admin.getAddress(), message, sign);
        log.info(responseData.getErrMsg());
        assertTrue(responseData.getResult());
    }

    @Test
    public void testFreezeNote() throws OpenLedgerBaseException {
        BigInteger nonce = authCenterSDK.getNonceFromAccount(admin.getAddress()).getResult();
        BigInteger noteNo = new BigInteger("20210001");
        byte[] message = OpenLedgerUtils.computeKeccak256Hash(OpenLedgerUtils.concatByte(OpenLedgerUtils.getBytes32(noteNo.toByteArray()),
                OpenLedgerUtils.convertStringToAddressByte(admin.getAddress()),
                OpenLedgerUtils.getBytes32(nonce.toByteArray())
        ));
        ECDSASignatureResult sign = OpenLedgerUtils.sign(admin, message);

        ResponseData<Boolean> response = nonFungibleAssetService.freezeNote(noteNo, admin.getAddress(), message, sign);
        log.info(response.getErrMsg());

        assertTrue(response.getResult());
    }

    @Test
    public void testUnfreezeNote() throws OpenLedgerBaseException {
        BigInteger nonce = authCenterSDK.getNonceFromAccount(admin.getAddress()).getResult();
        BigInteger noteNo = new BigInteger("20210004");
        byte[] message = OpenLedgerUtils.computeKeccak256Hash(OpenLedgerUtils.concatByte(OpenLedgerUtils.getBytes32(noteNo.toByteArray()),
                OpenLedgerUtils.convertStringToAddressByte(admin.getAddress()),
                OpenLedgerUtils.getBytes32(nonce.toByteArray())
        ));
        ECDSASignatureResult sign = OpenLedgerUtils.sign(admin, message);
        ResponseData<BigInteger> response = nonFungibleAssetService.unfreezeNote(noteNo, admin.getAddress(), message, sign);
        log.info(response.getErrMsg());
        assertEquals(Note.EFFECTIVE_STATUS, response.getResult().intValue());

    }

    @Test
    public void testTearNote() throws Exception {
        BigInteger nonce = authCenterSDK.getNonceFromAccount(admin.getAddress()).getResult();
        BigInteger noteNo = new BigInteger("2021001");
        byte[] message = OpenLedgerUtils.computeKeccak256Hash(OpenLedgerUtils.concatByte(OpenLedgerUtils.getBytes32(noteNo.toByteArray()),
                OpenLedgerUtils.convertStringToAddressByte(admin.getAddress()),
                OpenLedgerUtils.getBytes32(nonce.toByteArray())
        ));
        ECDSASignatureResult sign = OpenLedgerUtils.sign(admin, message);
        ResponseData<Boolean> response = nonFungibleAssetService.tearNote(noteNo, admin.getAddress(), message, sign);
        log.info(response.getErrMsg());
        assertTrue(response.getResult());
    }

    @Test
    public void getTearNote() throws Exception {
        BigInteger nonce = authCenterSDK.getNonceFromAccount(admin.getAddress()).getResult();
        BigInteger noteNo = new BigInteger("2021001");
        byte[] message = OpenLedgerUtils.computeKeccak256Hash(OpenLedgerUtils.concatByte(
                OpenLedgerUtils.convertStringToAddressByte(admin.getAddress()),
                OpenLedgerUtils.getBytes32(nonce.toByteArray())
        ));
        ECDSASignatureResult sign = OpenLedgerUtils.sign(admin, message);
        List<BigInteger> response = nonFungibleAssetService.getTearNotes(admin.getAddress(), message, sign);
        log.info(response.toString());
    }

    @Test
    public void addBook() throws OpenLedgerBaseException {
        BigInteger nonce = authCenterSDK.getNonceFromAccount(admin.getAddress()).getResult();
        byte[] message = OpenLedgerUtils.computeKeccak256HashFromBigInteger(nonce);
        // 交易参数
        ResponseData<BigInteger> responseData = nonFungibleAssetService.addBook(message, OpenLedgerUtils.sign(admin, message));
        assertTrue(ErrorCode.SUCCESS.getCode() == responseData.getErrorCode().intValue());
        log.info("booknum:" + responseData.getResult());
    }

    @Test
    public void effectBatch() throws OpenLedgerBaseException {
        BigInteger batchNo = BigInteger.valueOf(2);
        BigInteger nonce = authCenterSDK.getNonceFromAccount(admin.getAddress()).getResult();

        byte[] message = OpenLedgerUtils.computeKeccak256Hash(OpenLedgerUtils.concatByte(OpenLedgerUtils.getBytes32(batchNo.toByteArray()),
                OpenLedgerUtils.getBytes32(nonce.toByteArray())
        ));
        ECDSASignatureResult sign = OpenLedgerUtils.sign(admin, message);
        ResponseData<Boolean> responseData = nonFungibleAssetService.effectBatch(batchNo, message, sign);
        log.info(responseData.getErrMsg());
        assertTrue(ErrorCode.SUCCESS.getCode() == responseData.getErrorCode().intValue());

    }

    @Test
    public void query() throws Exception {
        BigInteger rightTermNo = BigInteger.valueOf(1);
        BigInteger rightSeq = BigInteger.valueOf(8807);
        BigInteger note = BigInteger.valueOf(203100099);
        // args
        String operatorAddress = user.getAddress();
        BigInteger nonce = authCenterSDK.getNonceFromAccount(admin.getAddress()).getResult();
        byte[] message = OpenLedgerUtils.computeKeccak256HashFromBigInteger(nonce);
        ECDSASignatureResult rs = OpenLedgerUtils.sign(admin, message);
        String account1 = admin.getAddress();
        String account2 = user.getAddress();

        List<BigInteger> limits = new ArrayList<>();

        NonFungibleCondition condition1 = new NonFungibleCondition(rightTermNo, null, null, account1, null, null);
        List<NonFungibleAssetRecord> recordEntities = nonFungibleAssetService.query(condition1, message, rs);
        assertNotNull(recordEntities);
        assertTrue(recordEntities.size() == 1);
        recordEntities.stream().forEach(item -> System.out.println(item.toString()));

        NonFungibleCondition condition2 = new NonFungibleCondition(BigInteger.valueOf(0), rightSeq, account1, account2);
        recordEntities = nonFungibleAssetService.query(condition2, message, rs);
        assertNotNull(recordEntities);
        recordEntities.stream().forEach(item -> System.out.println(item.toString()));
        System.out.println("=====================================");
        assertTrue(recordEntities.size() == 1);

        NonFungibleCondition condition3 = new NonFungibleCondition(rightTermNo, BigInteger.valueOf(0), account1, account2);
        recordEntities = nonFungibleAssetService.query(condition3, message, rs);
        assertNotNull(recordEntities);
        recordEntities.stream().forEach(item -> System.out.println(item));
        System.out.println("=====================================");
        assertTrue(recordEntities.size() >= 1);

        NonFungibleCondition condition4 = new NonFungibleCondition(BigInteger.valueOf(0), BigInteger.valueOf(0), account1, account2);
        recordEntities = nonFungibleAssetService.query(condition4, message, rs);
        assertNotNull(recordEntities);
        recordEntities.stream().forEach(item -> System.out.println(item));
        System.out.println("=====================================");
        assertTrue(recordEntities.size() >= 1);

        NonFungibleCondition condition5 = new NonFungibleCondition(BigInteger.valueOf(0), BigInteger.valueOf(0), account1, null);
        recordEntities = nonFungibleAssetService.query(condition5, message, rs);
        assertNotNull(recordEntities);
        recordEntities.stream().forEach(item -> System.out.println(item));
        System.out.println("=====================================");
        assertTrue(recordEntities.size() >= 1);

        NonFungibleCondition condition7 = new NonFungibleCondition(BigInteger.valueOf(3), rightSeq, account1, account2);
        recordEntities = nonFungibleAssetService.query(condition7, message, rs);
        assertTrue(recordEntities == null || recordEntities.size() == 0);

        NonFungibleCondition condition8 = new NonFungibleCondition(rightTermNo, BigInteger.valueOf(363), account1, account2);
        recordEntities = nonFungibleAssetService.query(condition8, message, rs);
        assertTrue(recordEntities == null || recordEntities.size() == 0);


        // 只能查詢自己的賬本 即from/to 是查詢者
        nonce = authCenterSDK.getNonceFromAccount(user.getAddress()).getResult();
        message = OpenLedgerUtils.computeKeccak256HashFromBigInteger(nonce);
        rs = OpenLedgerUtils.sign(user, message);
        NonFungibleCondition condition6 = new NonFungibleCondition(BigInteger.valueOf(0), BigInteger.valueOf(0), null, account2);
        recordEntities = nonFungibleAssetService.query(condition6, message, rs);
        assertNotNull(recordEntities);
        recordEntities.stream().forEach(item -> System.out.println(item));
        System.out.println("=====================================");
        assertTrue(recordEntities.size() >= 1);

        NonFungibleCondition condition9 = new NonFungibleCondition(rightTermNo, rightSeq, account2, account2);
        recordEntities = nonFungibleAssetService.query(condition9, message, rs);
        assertTrue(recordEntities == null || recordEntities.size() == 0);


    }


    @Test
    public void queryBookByAdmin() throws Exception {
        BigInteger rightTermNo = BigInteger.valueOf(1);
        BigInteger rightSeq = BigInteger.valueOf(9);
        // 交易参数
        String operatorAddress = user.getAddress();
        BigInteger nonce = authCenterSDK.getNonceFromAccount(admin.getAddress()).getResult();
        byte[] message = OpenLedgerUtils.computeKeccak256HashFromBigInteger(nonce);
        ECDSASignatureResult rs = OpenLedgerUtils.sign(admin, message);
        List<BigInteger> limits = new ArrayList<>();
        limits.add(BigInteger.valueOf(1));
        limits.add(BigInteger.valueOf(500));

        //组织管理者运行查询全部
        NonFungibleCondition condition10 = new NonFungibleCondition(rightTermNo, null, null, null, limits);
        List<NonFungibleAssetRecord> recordEntities = nonFungibleAssetService.query(condition10, message, rs);
        assertNotNull(recordEntities);
        recordEntities.stream().forEach(item -> log.info(item.toString()));
        log.info("=====================================");
        assertTrue(recordEntities.size() >= 1);

    }


}