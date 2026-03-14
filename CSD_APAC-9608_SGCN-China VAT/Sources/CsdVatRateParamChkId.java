package com.temenos.csd.vat;

import com.temenos.api.TField;
import com.temenos.api.exceptions.T24CoreException;
import com.temenos.t24.api.complex.eb.templatehook.TransactionContext;
import com.temenos.t24.api.hook.system.RecordLifecycle;
import com.temenos.t24.api.records.ebcsdstoreplcategparam.EbCsdStorePlCategParamRecord;
import com.temenos.t24.api.system.DataAccess;
import com.temenos.t24.api.system.Session;

/**
 * @author v.manoj
 * EB.API>CSD.VAT.RATE.PARAM.CHK.ID
 * Attached To: EB.TABLE.PROCEDURES>EB.CSD.VAT.WHT.RATE.PARAM
 * Attached As: CKECK.ID.PROC
 * Description: Routine to check the duplication category records is available in the table.
 * 
 * @update v.manoj - TSR-1160810 - VAT Delivery: VAT Parameter configuration issue
 */
public class CsdVatRateParamChkId extends RecordLifecycle {

    @Override
    public String checkId(String currentRecordId, TransactionContext transactionContext) {
        boolean duplicationFlag = false;
        DataAccess da = new DataAccess(this);
        Session ySession = new Session(this);
        //Throw error, if the ID should not be numeric value
        checkIntegerVal(currentRecordId);
        // Check the record is already available in the parameterized table.
        // If not check the range with the record available in the table.
        boolean chkRecFlag = chkRecord(da, currentRecordId, ySession);
        if (chkRecFlag) {
            return currentRecordId;
        }
        
        EbCsdStorePlCategParamRecord yPlCategParamRec = getStorePlCategParamRec(da, ySession);
        int[] currentRange = parseRange(currentRecordId);
        for (TField paramId : yPlCategParamRec.getPlCategParam()) {
            int[] existingRange = parseRange(paramId.getValue());
            // Exact match check
            if (currentRange[0] == existingRange[0] && currentRange[1] == existingRange[1]) {
                duplicationFlag = true;
            }
            // Overlap check
            if (currentRange[0] <= existingRange[1] && existingRange[0] <= currentRange[1]) {
                // Find first overlapping value
                duplicationFlag = true;
            }
        }
        //If Duplication range is found, throw error
        if (duplicationFlag) {
            throw new T24CoreException("", "EB-CSD.CATEG.ERR");
        }
        return currentRecordId;
    }

    //
    private int[] parseRange(String currentRecordId) {
        if (currentRecordId.contains("-")) {
            String[] parts = currentRecordId.split("-");
            int start = Integer.parseInt(parts[0].trim());
            int end = Integer.parseInt(parts[1].trim());
            // If reversed range → swap automatically
            if (start > end) {
                int temp = start;
                start = end;
                end = temp;
            }
            return new int[]{start, end};
        } else {
            int num = Integer.parseInt(currentRecordId.trim());
            return new int[]{num, num};
        }
    }

    //
    private EbCsdStorePlCategParamRecord getStorePlCategParamRec(DataAccess da, Session ySession) {
        try {
            return new EbCsdStorePlCategParamRecord(
                    da.getRecord(ySession.getCompanyRecord().getFinancialMne().getValue(),
                            "EB.CSD.STORE.PL.CATEG.PARAM", "", "SYSTEM"));
        } catch (Exception e) {
            return new EbCsdStorePlCategParamRecord(this);
        }
    }

    //
    private void checkIntegerVal(String currentRecordId) {
        String tableIdRegex = "^[0-9]+(-[0-9]+)?$";
        if (!currentRecordId.matches(tableIdRegex)) {
            throw new T24CoreException("", "EB-CSD.ID.NOT.NUMERIC");
        }
    }

    //
    private boolean chkRecord(DataAccess da, String currentRecordId, Session ySession) {
        boolean yChkRecFlag = false;
        try {
            da.getRecord(ySession.getCompanyRecord().getFinancialMne().getValue(),
                    "EB.CSD.VAT.WHT.RATE.PARAM", "", currentRecordId);
            yChkRecFlag = true;
        } catch (Exception e) {
            //
        }
        return yChkRecFlag;
    }

}
