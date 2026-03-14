package com.temenos.csd.vat;

import java.util.List;

import com.temenos.t24.api.complex.eb.servicehook.ServiceData;
import com.temenos.t24.api.hook.system.ServiceLifecycle;
import com.temenos.t24.api.records.acchargerequest.AcChargeRequestRecord;
import com.temenos.t24.api.records.account.AccountRecord;
import com.temenos.t24.api.records.customer.CustomerRecord;
import com.temenos.t24.api.records.ebcsdacctentdetails.EbCsdAcctEntDetailsRecord;
import com.temenos.t24.api.records.ebcsdtaxdetailsupd.EbCsdTaxDetailsUpdRecord;
import com.temenos.t24.api.records.ebcsdtaxdetailsupd.FeeTransactionReferenceClass;
import com.temenos.t24.api.records.fundstransfer.FundsTransferRecord;
import com.temenos.t24.api.system.DataAccess;
import com.temenos.t24.api.system.Session;
import com.temenos.t24.api.tables.ebcsdacctentdetails.EbCsdAcctEntDetailsTable;
import com.temenos.t24.api.tables.ebcsdacctnodetsupd.EbCsdAcctNoDetsUpdTable;
import com.temenos.t24.api.tables.ebcsdtaxdetailsupd.EbCsdTaxDetailsUpdTable;
import com.temenos.t24.api.records.ebcsdacctnodetsupd.EbCsdAcctNoDetsUpdRecord;
import com.temenos.t24.api.records.ebcsdtaxdetailsupd.ClientBrdIdClass;

/**
 * @author v.manoj EB.API>CSD.B.TRANS.DETAILS.UPD Attached To:
 *         BATCH>BNK/CSD.B.TRANS.DETAILS.UPD Attached As: Batch Post Routine
 *         Description: Routine to update the live table with the transaction
 *         details
 * @update v.manoj - TSR-1151911 - China VAT Delivery: Issue with LCY Amounts
 */
public class CsdBTransDetailsUpd extends ServiceLifecycle {

    DataAccess yDataAccess = new DataAccess();
    Session ySess = new Session();
    AcChargeRequestRecord yAcChrgReq = new AcChargeRequestRecord();
    FundsTransferRecord yFtRecord = new FundsTransferRecord();
    String acctEntDetsNm = "EB.CSD.ACCT.ENT.DETAILS";
    String delimiter = "###";
    
    @Override
    public void processSingleThreaded(ServiceData serviceData) {
        ySess = new Session(this);
        yDataAccess = new DataAccess(this);
        yAcChrgReq = new AcChargeRequestRecord(this);
        yFtRecord = new FundsTransferRecord(this);
        
        // get the account number list
        List<String> acctList = yDataAccess
                .selectRecords(ySess.getCompanyRecord().getMnemonic().getValue(), "EB.CSD.ACCT.NO.DETS.UPD", "", "");
        for (String acctId : acctList) {
            EbCsdAcctNoDetsUpdRecord yAcctNoDetsRec = getAcctNoDetsRec(acctId);
            String transAcctNo = getAcctNo(acctId, yAcctNoDetsRec);
            // Get the Account Details
            AccountRecord yAcctRec = getAcctDets(transAcctNo);
            // Get the customer details
            String yCustId = yAcctNoDetsRec.getBestFitCustId().getValue();
            CustomerRecord yCustRec = getCustDetails(yCustId);
            // Check the account record available in the Live table
            EbCsdTaxDetailsUpdRecord taxDetsUpdRec = checkLiveTable(transAcctNo, yAcctRec, yCustRec, yCustId);
            List<String> acctEntList = yDataAccess
               .selectRecords(ySess.getCompanyRecord().getMnemonic().getValue(), acctEntDetsNm, "", "WITH @ID LIKE ..."+ acctId +"...");
            for (String acctEntId : acctEntList) { // For the same account multiple fee happen
                EbCsdAcctEntDetailsRecord yAcctEntDetsRec = new EbCsdAcctEntDetailsRecord(yDataAccess
                        .getRecord(ySess.getCompanyRecord().getFinancialMne().getValue(),
                                acctEntDetsNm, "", acctEntId));
                // Object for Fee Transaction Reference
                FeeTransactionReferenceClass feeTransReference = new FeeTransactionReferenceClass();
                try {
                    String transRef = yAcctEntDetsRec.getAccountingEntry().getValue().split("#")[0].split(",")[7];
                    feeTransReference.setFeeTransactionReference(transRef);
                    String transDets = yAcctEntDetsRec.getTransConcatDets().getValue();
                    int delimLen = transDets.split(delimiter).length;
                    updChargeDets(transDets, feeTransReference);
                    String feeTransType = transDets.split(delimiter)[1];
                    feeTransReference.setFeeTransactionType(feeTransType);
                    String feeAmount = transDets.split(delimiter)[3];
                    feeTransReference.setFeeAmount(feeAmount);
                    String feeCurrency = transDets.split(delimiter)[4];
                    feeTransReference.setFeeCurrency(feeCurrency);
                    String dteOfFeeTrans = transDets.split(delimiter)[5];
                    feeTransReference.setDateOfFeeTransaction(dteOfFeeTrans);
                    String valDteOfFeeTrans = transDets.split(delimiter)[6];
                    feeTransReference.setValueDateOfFeeTransaction(valDteOfFeeTrans);
                    String vatExchngRateforFee = transDets.split(delimiter)[7];
                    feeTransReference.setVatExchangeRateForFee(vatExchngRateforFee);
                    String outVatRateForFee = transDets.split(delimiter)[8];
                    feeTransReference.setOutputVatRateForFee(outVatRateForFee);
                    String outVatFcyAmtForFee = transDets.split(delimiter)[9];
                    feeTransReference.setOutputVatFcyAmountForFee(outVatFcyAmtForFee);
                    String outVatLcyAmtForFee = transDets.split(delimiter)[10];
                    feeTransReference.setOutputVatLcyAmountForFee(outVatLcyAmtForFee);
                    /** if (outVatFcyAmtForFee.isBlank()) {
                        String outVatLcyAmtForFee = transDets.split(delimiter)[10];
                        feeTransReference.setOutputVatLcyAmountForFee(outVatLcyAmtForFee);
                    } */
                    if (delimLen > 11) {
                        String inpVatRateForFee = transDets.split(delimiter)[11];
                        feeTransReference.setInputVatRateForFee(inpVatRateForFee);
                        String inpVatCalculationTypeForFee =transDets.split(delimiter)[14];
                        feeTransReference.setInputVatCalcTypeForFee(inpVatCalculationTypeForFee);
                        String inpVatFcyAmountForFee = transDets.split(delimiter)[13];
                        feeTransReference.setInputVatFcyAmountForFee(inpVatFcyAmountForFee);
                        String inpVatLcyAmountForFee = transDets.split(delimiter)[12];
                        feeTransReference.setInputVatLcyAmountForFee(inpVatLcyAmountForFee);
                        /** if(inpVatFcyAmountForFee.isBlank()) {
                            String inpVatLcyAmountForFee = transDets.split(delimiter)[12];
                            feeTransReference.setInputVatLcyAmountForFee(inpVatLcyAmountForFee);
                        } */
                        // Set the Reversal Transaction Fee and date 
                        if (delimLen > 14) {
                            String revFlag = transDets.split(delimiter)[15];
                            if (revFlag.equalsIgnoreCase("YES")) {
                                String transRevFee = transDets.split(delimiter)[17];
                                feeTransReference.setTransactionReversalForFee(transRevFee);
                                String transRevDate = transDets.split(delimiter)[16];
                                feeTransReference.setTransactionReversalDate(transRevDate);
                            }
                        }
                    }
                    // Count the overall size in a record, before append the current set
                    int yTotalSize = taxDetsUpdRec.getFeeTransactionReference().size();
                    taxDetsUpdRec.setFeeTransactionReference(feeTransReference, yTotalSize);
                    // Delete the accounting entry for the DebitLeg
                    delDebitAcctEnt(acctEntId);
                } catch (Exception e) {
                    //
                }
            }
            // Write the Transaction details into Live Table
            writeDetstoLiveTable(taxDetsUpdRec, transAcctNo);
            // Delete the account Number
            delAcctNo(acctId);
        }
    }

    //
    private EbCsdAcctNoDetsUpdRecord getAcctNoDetsRec(String acctId) {
        EbCsdAcctNoDetsUpdRecord yAcctNoDetsRec = new EbCsdAcctNoDetsUpdRecord(this);
        try {
            yAcctNoDetsRec = new EbCsdAcctNoDetsUpdRecord(yDataAccess.
              getRecord(ySess.getCompanyRecord().getMnemonic().getValue(), "EB.CSD.ACCT.NO.DETS.UPD", "", acctId));
        } catch (Exception e) {
            // 
        }
        return yAcctNoDetsRec;
    }

    //
    private String getAcctNo(String acctId, EbCsdAcctNoDetsUpdRecord yAcctNoDetsRec) {
        String acctNumber = "";
        if (acctId.startsWith("CHG")) {
            acctNumber = getChgeReqAcctNo(acctId);
        } else if (acctId.startsWith("FT")) {
            acctNumber = getFtAcctNo(acctId, yAcctNoDetsRec);
        } else {
            acctNumber = acctId;
        }
        return acctNumber;
    }

    //
    private String getChgeReqAcctNo(String acctId) {
        String yAcAcctNo = "";
        try {
            yAcChrgReq = new AcChargeRequestRecord(yDataAccess
                    .getRecord(ySess.getCompanyRecord().getFinancialMne().getValue(),
                            "AC.CHARGE.REQUEST", "", acctId));
            yAcAcctNo = yAcChrgReq.getDebitAccount().getValue();
        } catch (Exception e) {
            //
        }
        return yAcAcctNo;
    }

    //
    private void delAcctNo(String acctEntId) {
        EbCsdAcctNoDetsUpdTable yAcctNoDetsUpdTab = new EbCsdAcctNoDetsUpdTable(this);
        try {
            yAcctNoDetsUpdTab.delete(acctEntId);
        } catch (Exception e) {
            //
        }
    }

    //
    private void delDebitAcctEnt(String acctEntId) {
        EbCsdAcctEntDetailsTable yAcctDetsTabRec = new EbCsdAcctEntDetailsTable(this);
        try {
            yAcctDetsTabRec.delete(acctEntId);
        } catch (Exception e) {
            //
        }
    }
    
    //
    private FeeTransactionReferenceClass updChargeDets(String transDets, FeeTransactionReferenceClass feeTransReference) {
        String paymentType = transDets.split(delimiter)[0];
        if (paymentType.equals("CH") || paymentType.equals("AC")) {
            String charegCode = getChargeCodeValue();
            feeTransReference.setChargeDetail(charegCode);
        } else if (paymentType.equals("FT")) {
            String ftcharegCode = getFtCommissionType();
            feeTransReference.setChargeDetail(ftcharegCode);
        } else {
            feeTransReference.setChargeDetail(transDets.split(delimiter)[2]);
        }
        return feeTransReference;
    }

    //
    private String getFtCommissionType() {
        String ftChargeCode = "";
        try {
            ftChargeCode = yFtRecord
                    .getCommissionType(yFtRecord.getCommissionType().size()-1).getCommissionType().getValue();
        } catch (Exception e) {
            // 
        }
        return ftChargeCode;
    }

    //
    private String getFtAcctNo(String acctId, EbCsdAcctNoDetsUpdRecord yAcctNoDetsRec) {
        String accountNo = "";
        try {
            yFtRecord = new FundsTransferRecord(yDataAccess.getRecord(
                    ySess.getCompanyRecord().getFinancialMne().getValue(), "FUNDS.TRANSFER", "", acctId));
            String acctTaxType = yAcctNoDetsRec.getAcctTaxType().getValue();
            String acctTaxTypeVal = acctTaxType.split("-")[0];
            String revEntFeeFlag = acctTaxType.split("-")[1];
            
            if (acctTaxTypeVal.equalsIgnoreCase("Output")) {
                accountNo = yFtRecord.getDebitAcctNo().getValue();
                if (revEntFeeFlag.equalsIgnoreCase("R")) {
                    accountNo = yFtRecord.getCreditAcctNo().getValue();
                }
            } else {
                accountNo = yFtRecord.getCreditAcctNo().getValue();
                if (revEntFeeFlag.equalsIgnoreCase("R")) {
                    accountNo = yFtRecord.getDebitAcctNo().getValue();
                }
            }
        } catch (Exception e) {
            //
        }
        return accountNo;
    }

    //
    private String getChargeCodeValue() {
        String charegCode = "";
        try {
            charegCode = yAcChrgReq.getChargeCode(yAcChrgReq.getChargeCode().size() - 1).getChargeCode().getValue();
        } catch (Exception e) {
            //
        }
        return charegCode;
    }

    //
    private void writeDetstoLiveTable(EbCsdTaxDetailsUpdRecord taxDetsUpdRec, String acctId) {
        if (acctId.startsWith("CHG")) {
            acctId = yAcChrgReq.getDebitAccount().getValue();
        }
        try {
            EbCsdTaxDetailsUpdTable taxDetsUpdtable = new EbCsdTaxDetailsUpdTable(this);
            taxDetsUpdtable.write(acctId, taxDetsUpdRec);
        } catch (Exception e) {
            //
        }
    }

    private CustomerRecord getCustDetails(String yCustId) {
        CustomerRecord yCustRec = new CustomerRecord(this);
        try {
            yCustRec = new CustomerRecord(yDataAccess.
              getRecord(ySess.getCompanyRecord().getFinancialMne().getValue(), "CUSTOMER", "", yCustId));
                                                                          
        } catch (Exception e) {
            //
        }
        return yCustRec;
    }

    //
    private AccountRecord getAcctDets(String acctNumber) {
        AccountRecord acctRecord = new AccountRecord(this);
        try {
            acctRecord = new AccountRecord(yDataAccess
                    .getRecord(ySess.getCompanyRecord().getFinancialMne().getValue(),
                            "ACCOUNT", "", acctNumber));
        } catch (Exception e) {
            //
        }
        return acctRecord;
    }

    //
    private EbCsdTaxDetailsUpdRecord checkLiveTable(String acctNumber, AccountRecord yAcctRec,
            CustomerRecord yCustRec, String yCustId) {
        EbCsdTaxDetailsUpdRecord taxDetsUpdRec = new EbCsdTaxDetailsUpdRecord(this);
        try {
            taxDetsUpdRec = new EbCsdTaxDetailsUpdRecord(yDataAccess
                .getRecord(ySess.getCompanyRecord().getFinancialMne().getValue(),
                        "EB.CSD.TAX.DETAILS.UPD", "", acctNumber));
            updateClientId(taxDetsUpdRec,  yCustRec, yCustId);
        } catch (Exception e) {
            ClientBrdIdClass custBrdIdSetObj = new ClientBrdIdClass();
            custBrdIdSetObj.setClientBrdId(yCustId);
            custBrdIdSetObj.setClientCategory(yCustRec.getSector().getValue());
            custBrdIdSetObj.setClientLocation(yCustRec.getRegCountry().getValue());
            taxDetsUpdRec.setClientBrdId(custBrdIdSetObj, 0);
            taxDetsUpdRec.setAccountNumber(acctNumber);
            taxDetsUpdRec.setArrangementNumber(yAcctRec.getArrangementId().getValue());
            taxDetsUpdRec.setAccountCategory(yAcctRec.getCategory().getValue());
            taxDetsUpdRec.setAccountCurrency(yAcctRec.getCurrency().getValue());
        }
        return taxDetsUpdRec;
    }

    //
    private EbCsdTaxDetailsUpdRecord updateClientId(EbCsdTaxDetailsUpdRecord taxDetsUpdRec, CustomerRecord yCustRec, String yCustId) {
        boolean custIdFlag = false;
                             
        for (ClientBrdIdClass CustBrdIdSet : taxDetsUpdRec.getClientBrdId()) {
            if (CustBrdIdSet.getClientBrdId().getValue().equals(yCustId)) {
                custIdFlag = true;
                break;
            }
        }
        if (!custIdFlag) {
            ClientBrdIdClass yCltBrdIdSet = new ClientBrdIdClass();
            yCltBrdIdSet.setClientBrdId(yCustId);
            yCltBrdIdSet.setClientCategory(yCustRec.getSector().getValue());
            yCltBrdIdSet.setClientLocation(yCustRec.getRegCountry().getValue());
            taxDetsUpdRec.setClientBrdId(yCltBrdIdSet, taxDetsUpdRec.getClientBrdId().size());
        }
        return taxDetsUpdRec;
    }

}