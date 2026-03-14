$PACKAGE CSD.APAC_9608_CSD_SGCN_ChinaVAT
SUBROUTINE CSD.B.BOOK.DATE.UPDATE.SELECT
*-----------------------------------------------------------------------------
* Description  : Select Routine to select the record from EB.CSD.ACCT.ENT.DETAILS.EOD
* Developed by : Manoj V
* Dev. Ref     : CSD_APAC-9608
* Attached To  : BATCH>BNK/COMW.B.RR.INITIAL.UPDATE
* Attached As  : Select Routine
*-----------------------------------------------------------------------------
* Modification History :
*-----------------------------------------------------------------------------

*-----------------------------------------------------------------------------
    $INSERT I_COMMON
    $INSERT I_EQUATE
    $INSERT I_CSD.B.BOOK.DATE.UPDATE.COMMON
    
* SEL.TEMP.FILE = 'SELECT ':FN.EB.CSD.ACCT.ENT.DETAILS.EOD: ' WITH @ID LK ...':'EOD'
    SEL.TEMP.FILE = 'SELECT ':FN.EB.CSD.ACCT.ENT.DETAILS.EOD
    CALL EB.READLIST(SEL.TEMP.FILE,TEMP.FILE.LIST,'',NO.FILE,Y.TEMP.FIILE.ERR)
    CALL BATCH.BUILD.LIST('',TEMP.FILE.LIST)
*CALL F.DELETE(FN.FILE.RD,Y.FILE.ID)

RETURN
END
