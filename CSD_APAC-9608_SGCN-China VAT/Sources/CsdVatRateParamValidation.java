package com.temenos.csd.vat;

import com.temenos.api.TStructure;
import com.temenos.api.TValidationResponse;
import com.temenos.t24.api.complex.eb.templatehook.TransactionContext;
import com.temenos.t24.api.hook.system.RecordLifecycle;
import com.temenos.t24.api.records.ebcsdvatwhtrateparam.ClientCategoryClass;
import com.temenos.t24.api.records.ebcsdvatwhtrateparam.EbCsdVatWhtRateParamRecord;

/**
 * @author v.manoj
 * EB.API>CSD.VAT.RATE.PARAM.VALIDATION
 * Attached To: EB.TABLE.PROCEDURES>EB.CSD.VAT.WHT.RATE.PARAM
 * Attached As: CROSSVAL.PROC
 * Description: Routine to validate the Input VAT calculation type, WHT Calculation Type
 *              and WHT rate field in a table.
 *
 */
public class CsdVatRateParamValidation extends RecordLifecycle {

    @Override
    public TValidationResponse validateRecord(String application, String currentRecordId, TStructure currentRecord,
            TStructure unauthorisedRecord, TStructure liveRecord, TransactionContext transactionContext) {
        EbCsdVatWhtRateParamRecord yVatWhtRateParamRec = new EbCsdVatWhtRateParamRecord(currentRecord);
        int i = 0;
        for (ClientCategoryClass clientCategSet : yVatWhtRateParamRec.getClientCategory()) {
            vatWhtTypeValidation(yVatWhtRateParamRec, clientCategSet, i);
            i++;
        }
        return yVatWhtRateParamRec.getValidationResponse();
    }

    //
    private EbCsdVatWhtRateParamRecord vatWhtTypeValidation(EbCsdVatWhtRateParamRecord yVatWhtRateParamRec, ClientCategoryClass clientCategSet, int i) {
        String errMsgId = "EB-CSD.TAX.TYPE.ERR";
        String taxValue = clientCategSet.getTaxType().getValue();
        if (taxValue.isEmpty() || !taxValue.contains("Input")) {
            if (!clientCategSet.getInputVatCalculationType().getValue().isBlank()) {
                yVatWhtRateParamRec.getClientCategory(i).getInputVatCalculationType().setError(errMsgId);
            }
            if (!clientCategSet.getWhtCalculationType().getValue().isBlank()) {
                yVatWhtRateParamRec.getClientCategory(i).getWhtCalculationType().setError(errMsgId);
            }
            if (!clientCategSet.getWhtRate().getValue().isBlank()) {
                yVatWhtRateParamRec.getClientCategory(i).getWhtRate().setError(errMsgId);
            }
        }
        return yVatWhtRateParamRec;
    }

}
