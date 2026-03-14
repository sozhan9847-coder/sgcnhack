package com.temenos.csd.vat;

import java.util.List;

import com.temenos.t24.api.complex.eb.servicehook.ServiceData;
import com.temenos.t24.api.hook.system.ServiceLifecycle;
import com.temenos.t24.api.system.DataAccess;
import com.temenos.logging.facade.Logger;
import com.temenos.logging.facade.LoggerFactory;

/**
 * @author v.manoj
 * EB.API>CSD.B.TRANS.ACCT.ENT.SELECT
 * Attached To: BATCH>BNK/CSD.B.TRANS.ACCT.ENT
 * Attached As: Batch Select Routine
 * Description: Routine to select the record from the EB.CSD.ACCT.ENT.DETAILS
 */
public class CsdBTransAcctEntSelect extends ServiceLifecycle {
    
    private static Logger logger = LoggerFactory.getLogger("API");
    
    @Override
    public List<String> getIds(ServiceData serviceData, List<String> controlList) {
        logger.debug(" **************** Batch Select -> Start **************** ");
        DataAccess da = new DataAccess(this);
        logger.debug("List of Id Selected --- "+da.selectRecords("", "EB.CSD.ACCT.ENT.DETAILS", "", ""));
        logger.debug(" **************** Batch Select -> End **************** ");
        return da.selectRecords("", "EB.CSD.ACCT.ENT.DETAILS", "", "");
    }

}
