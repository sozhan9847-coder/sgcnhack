package com.temenos.csd.vat;

import com.temenos.api.exceptions.T24CoreException;
import com.temenos.t24.api.complex.eb.templatehook.TransactionContext;
import com.temenos.t24.api.hook.system.RecordLifecycle;

/**
 * @author v.manoj
 * EB.API>CSD.TAX.DETS.PARAM.CHK.ID
 * Attached To: EB.TABLE.PROCEDURES>EB.CSD.TAX.DETS.PARAM
 * Attached As: CKECK.ID.PROC
 * Description: Routine to validate the recordId in the table.
 *
 */
public class CsdTaxDetsParamChkId extends RecordLifecycle {
    
    @Override
    public String checkId(String currentRecordId, TransactionContext transactionContext) {
        if (!currentRecordId.equals("SYSTEM")) {
            throw new T24CoreException("", "EB-ID.SYSTEM");
        }
        return currentRecordId;
    }
}
