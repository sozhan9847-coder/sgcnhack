package com.temenos.csd.vat;

import java.util.List;

import com.temenos.logging.facade.Logger;
import com.temenos.logging.facade.LoggerFactory;
import com.temenos.t24.api.complex.eb.servicehook.ServiceData;
import com.temenos.t24.api.hook.system.ServiceLifecycle;
import com.temenos.t24.api.records.ebcsdpostadjtaxdetsupd.EbCsdPostAdjTaxDetsUpdRecord;
import com.temenos.t24.api.records.ebcsdtaxdetailsupd.EbCsdTaxDetailsUpdRecord;
import com.temenos.t24.api.records.ebcsdtaxdetailsupd.InterestDetailClass;
import com.temenos.t24.api.records.ebcsdtaxdetailsupd.PeriodStartClass;
import com.temenos.t24.api.system.DataAccess;
import com.temenos.t24.api.system.Session;
import com.temenos.t24.api.tables.ebcsdpostadjtaxdetsupd.EbCsdPostAdjTaxDetsUpdTable;
import com.temenos.t24.api.tables.ebcsdtaxdetailsupd.EbCsdTaxDetailsUpdTable;

/**
 * @author v.manoj
 * EB.API>CSD.B.ADJ.CAP.TRANS.UPD.SELECT
 *        CSD.B.ADJ.CAP.TRANS.UPD
 * Attached To: BATCH>BNK/CSD.B.ADJ.TRANS.POST.COB
 * Attached As: Batch Post Routine
 * Description: Routine to update the live table for the Adjust Cap Schedule details.
 */
public class CsdBAdjCapTransUpd extends ServiceLifecycle {

    DataAccess da = new DataAccess(this);
    Session ySess = new Session(this);
    private static Logger logger = LoggerFactory.getLogger("API");
    
    @Override
    public List<String> getIds(ServiceData serviceData, List<String> controlList) {
        logger.debug("String Selected record from EB.CSD.POST.ADJ.TAX.DETS.UPD = "
              + da.selectRecords(ySess.getCompanyRecord().getFinancialMne().getValue(), "EB.CSD.POST.ADJ.TAX.DETS.UPD", "", ""));
        return da.selectRecords(ySess.getCompanyRecord().getFinancialMne().getValue(), "EB.CSD.POST.ADJ.TAX.DETS.UPD",
                "", "");
    }

    @Override
    public void process(String id, ServiceData serviceData, String controlItem) {
        logger.debug(" **** Process Routine called **** ");
        logger.debug("String incomingId = "+ id);
        try {
            EbCsdPostAdjTaxDetsUpdRecord yPostAdjTaxDets = new EbCsdPostAdjTaxDetsUpdRecord(da.getRecord(
                    ySess.getCompanyRecord().getFinancialMne().getValue(), "EB.CSD.POST.ADJ.TAX.DETS.UPD", "", id));
            logger.debug("String yPostAdjTaxDets = "+ yPostAdjTaxDets);
            // Read the EB.CSD.TAX.DETAILS.UPD and get the account record.
            EbCsdTaxDetailsUpdRecord yTaxDetsUpdRec = new EbCsdTaxDetailsUpdRecord(da.getRecord(
                    ySess.getCompanyRecord().getFinancialMne().getValue(), "EB.CSD.TAX.DETAILS.UPD", "", id));
            logger.debug("String yTaxDetsUpdRec = "+ yTaxDetsUpdRec);
            int totalIntSize = yTaxDetsUpdRec.getInterestDetail().size();
            logger.debug("String totalIntSize = "+ totalIntSize);
            if (totalIntSize == 0) {
                logger.debug(" New Interest Property Condition ad first ");
                // Creating Interest Set and set the required details
                InterestDetailClass yIntDetailObj = new InterestDetailClass();
                yIntDetailObj.setInterestDetail(yPostAdjTaxDets.getInterestDetail());
                yIntDetailObj.setInterestTransactionType(yPostAdjTaxDets.getInterestTransactionType());
                yIntDetailObj.setInterestCurrency(yPostAdjTaxDets.getInterestCurrency().getValue());
                yIntDetailObj.setInputVatRateForInterest(yPostAdjTaxDets.getInputVatRateForInterest());
                yIntDetailObj.setVatExchangeRateForInterest(yPostAdjTaxDets.getVatExchangeRateForInterest());
                yIntDetailObj.setWhtExchangeRateForInterest(yPostAdjTaxDets.getWhtExchangeRateForInterest());
                yIntDetailObj.setWhtRateForInterest(yPostAdjTaxDets.getWhtRateForInterest());
                // Creating Period Set and set the required details
                PeriodStartClass yPeriodStrtObj = new PeriodStartClass();
                yPeriodStrtObj.setPeriodStart(yPostAdjTaxDets.getPeriodStart().getValue());
                yPeriodStrtObj.setPeriodEnd(yPostAdjTaxDets.getPeriodEnd().getValue());
                yPeriodStrtObj.setTotalIntAmount(yPostAdjTaxDets.getTotalIntAmount());
                yPeriodStrtObj.setTotInputValLcyAmtForInt(yPostAdjTaxDets.getTotInputValLcyAmtForInt());
                yPeriodStrtObj.setTotInputVatFcyAmtForInt(yPostAdjTaxDets.getTotInputVatFcyAmtForInt());
                yPeriodStrtObj.setTotWhtLcyAmtForInt(yPostAdjTaxDets.getTotWhtLcyAmtForInt());
                yPeriodStrtObj.setTotWhtFcyAmtForInt(yPostAdjTaxDets.getTotWhtFcyAmtForInt());
                // Set the Period Start class in the first position
                yIntDetailObj.setPeriodStart(yPeriodStrtObj, 0);
                logger.debug("String yInterestDets = " + yIntDetailObj);
                yTaxDetsUpdRec.setInterestDetail(yIntDetailObj, 0);
                logger.debug("String yTaxDetsRec = " + yTaxDetsUpdRec);
            } else {
                boolean tIntDetailFlg = false;
                for (InterestDetailClass tFIntDetails : yTaxDetsUpdRec.getInterestDetail()) {
                    if (tFIntDetails.getInterestDetail().getValue()
                            .equals(yPostAdjTaxDets.getInterestDetail().getValue())) {
                        logger.debug(" Identified Same Interest Condition ");
                        // Creating Period Set and set the required details
                        PeriodStartClass yPeriodStrtObj = new PeriodStartClass();
                        yPeriodStrtObj.setPeriodStart(yPostAdjTaxDets.getPeriodStart().getValue());
                        yPeriodStrtObj.setPeriodEnd(yPostAdjTaxDets.getPeriodEnd().getValue());
                        yPeriodStrtObj.setTotalIntAmount(yPostAdjTaxDets.getTotalIntAmount());
                        yPeriodStrtObj.setTotInputValLcyAmtForInt(yPostAdjTaxDets.getTotInputValLcyAmtForInt());
                        yPeriodStrtObj.setTotInputVatFcyAmtForInt(yPostAdjTaxDets.getTotInputVatFcyAmtForInt());
                        yPeriodStrtObj.setTotWhtLcyAmtForInt(yPostAdjTaxDets.getTotWhtLcyAmtForInt());
                        yPeriodStrtObj.setTotWhtFcyAmtForInt(yPostAdjTaxDets.getTotWhtFcyAmtForInt());
                        // appending the period set at the last
                        tFIntDetails.setPeriodStart(yPeriodStrtObj, tFIntDetails.getPeriodStart().size());
                        logger.debug("String tFIntDetails = " + tFIntDetails);
                        tIntDetailFlg = true;
                        break;
                    }
                }
                if (!tIntDetailFlg) {
                    logger.debug(" New Interest Property Condition ");
                    // Creating Interest Set and set the required details
                    InterestDetailClass intDetailObj = new InterestDetailClass();
                    intDetailObj.setInterestDetail(yPostAdjTaxDets.getInterestDetail());
                    intDetailObj.setInterestTransactionType(yPostAdjTaxDets.getInterestTransactionType());
                    intDetailObj.setInterestCurrency(yPostAdjTaxDets.getInterestCurrency().getValue());
                    intDetailObj.setInputVatRateForInterest(yPostAdjTaxDets.getInputVatRateForInterest());
                    intDetailObj.setVatExchangeRateForInterest(yPostAdjTaxDets.getVatExchangeRateForInterest());
                    intDetailObj.setWhtExchangeRateForInterest(yPostAdjTaxDets.getWhtExchangeRateForInterest());
                    intDetailObj.setWhtRateForInterest(yPostAdjTaxDets.getWhtRateForInterest());
                    // Creating Period Set and set the required details
                    PeriodStartClass periodStrtObj = new PeriodStartClass();
                    periodStrtObj.setPeriodStart(yPostAdjTaxDets.getPeriodStart().getValue());
                    periodStrtObj.setPeriodEnd(yPostAdjTaxDets.getPeriodEnd().getValue());
                    periodStrtObj.setTotalIntAmount(yPostAdjTaxDets.getTotalIntAmount());
                    periodStrtObj.setTotInputValLcyAmtForInt(yPostAdjTaxDets.getTotInputValLcyAmtForInt());
                    periodStrtObj.setTotInputVatFcyAmtForInt(yPostAdjTaxDets.getTotInputVatFcyAmtForInt());
                    periodStrtObj.setTotWhtLcyAmtForInt(yPostAdjTaxDets.getTotWhtLcyAmtForInt());
                    periodStrtObj.setTotWhtFcyAmtForInt(yPostAdjTaxDets.getTotWhtFcyAmtForInt());
                    logger.debug("String periodStrtObj = " + periodStrtObj);
                    // Set the Period Start class in the first position
                    intDetailObj.setPeriodStart(periodStrtObj, 0);
                    logger.debug("String yInterestDets = " + intDetailObj);
                    yTaxDetsUpdRec.setInterestDetail(intDetailObj, yTaxDetsUpdRec.getInterestDetail().size());
                    logger.debug("String yTaxDetsRec = " + yTaxDetsUpdRec);
                }
            }
            // Write Adjust Cap Schedule details into live table
            EbCsdTaxDetailsUpdTable yTaxDetsUpdTab = new EbCsdTaxDetailsUpdTable(this);
            yTaxDetsUpdTab.write(id, yTaxDetsUpdRec);
            // Delete the record from EB.CSD.POST.ADJ.TAX.DETS.UPD table
            EbCsdPostAdjTaxDetsUpdTable yPostAdjTaxDetsTab = new EbCsdPostAdjTaxDetsUpdTable(this);
            yPostAdjTaxDetsTab.delete(id);
            
        } catch (Exception e) {
            //
        }
        
    }

}
