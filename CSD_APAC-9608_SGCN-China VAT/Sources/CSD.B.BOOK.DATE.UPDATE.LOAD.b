$PACKAGE CSD.APAC_9608_CSD_SGCN_ChinaVAT
SUBROUTINE CSD.B.BOOK.DATE.UPDATE.LOAD
*-----------------------------------------------------------------------------
* Description  : Load Routine to Open the Required file
* Developed by : Manoj V
* Dev. Ref     : CSD_APAC-9608
* Attached To  : BATCH>BNK/COMW.B.RR.INITIAL.UPDATE
* Attached As  : Load Routine
*-----------------------------------------------------------------------------
* Modification History :
*-----------------------------------------------------------------------------

*-----------------------------------------------------------------------------
    $INSERT I_COMMON
    $INSERT I_EQUATE
    $INSERT I_F.ACCOUNT
    $INSERT I_CSD.B.BOOK.DATE.UPDATE.COMMON
    
    FN.ACC = 'F.ACCOUNT'
    F.ACC = ''
    CALL OPF(FN.ACC,F.ACC)

    FN.EB.CSD.ACCT.ENT.DETAILS.EOD = 'F.EB.CSD.ACCT.ENT.DETAILS.EOD'
    F.EB.CSD.ACCT.ENT.DETAILS.EOD = ''
    CALL OPF(FN.EB.CSD.ACCT.ENT.DETAILS.EOD,F.EB.CSD.ACCT.ENT.DETAILS.EOD)
RETURN
END
