package com.shanyuefang.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shanyuefang.common.exception.BusinessException;
import com.shanyuefang.common.result.ResultCode;
import com.shanyuefang.common.util.SnowflakeIdUtil;
import com.shanyuefang.user.domain.dto.CreditOperationDTO;
import com.shanyuefang.user.domain.entity.UserCreditAccount;
import com.shanyuefang.user.domain.entity.UserCreditLedger;
import com.shanyuefang.user.domain.vo.UserCreditVO;
import com.shanyuefang.user.mapper.UserCreditAccountMapper;
import com.shanyuefang.user.mapper.UserCreditLedgerMapper;
import com.shanyuefang.user.service.CreditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CreditServiceImpl implements CreditService {
    public static final int STARTER_CREDITS = 100;
    private final UserCreditAccountMapper accountMapper;
    private final UserCreditLedgerMapper ledgerMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grantStarterCredits(long userId) {
        CreditOperationDTO dto = new CreditOperationDTO();
        dto.setUserId(userId);
        dto.setAmount(STARTER_CREDITS);
        dto.setRequestId("register:" + userId);
        dto.setReason("New user agent trial credits");
        grant(dto);
    }

    @Override
    public UserCreditVO getCredits(long userId) {
        UserCreditAccount account = accountMapper.selectById(userId);
        if (account == null) {
            return new UserCreditVO(0, 0);
        }
        return new UserCreditVO(account.getAvailableCredits(), account.getFrozenCredits());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grant(CreditOperationDTO dto) {
        if (isRecorded(dto.getRequestId())) return;
        UserCreditAccount account = account(dto.getUserId());
        updateAccount(account, account.getAvailableCredits() + dto.getAmount(), account.getFrozenCredits());
        ledger(dto, "GRANT");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void freeze(CreditOperationDTO dto) {
        if (isRecorded(dto.getRequestId())) return;
        UserCreditAccount account = account(dto.getUserId());
        if (account.getAvailableCredits() < dto.getAmount()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Insufficient agent credits");
        }
        updateAccount(account, account.getAvailableCredits() - dto.getAmount(), account.getFrozenCredits() + dto.getAmount());
        ledger(dto, "FREEZE");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void settle(CreditOperationDTO dto) {
        if (isRecorded(dto.getRequestId())) return;
        UserCreditAccount account = account(dto.getUserId());
        if (account.getFrozenCredits() < dto.getAmount()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Invalid credit settlement");
        }
        updateAccount(account, account.getAvailableCredits(), account.getFrozenCredits() - dto.getAmount());
        ledger(dto, "SETTLE");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refund(CreditOperationDTO dto) {
        if (isRecorded(dto.getRequestId())) return;
        UserCreditAccount account = account(dto.getUserId());
        if (account.getFrozenCredits() < dto.getAmount()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Invalid credit refund");
        }
        updateAccount(account, account.getAvailableCredits() + dto.getAmount(), account.getFrozenCredits() - dto.getAmount());
        ledger(dto, "REFUND");
    }

    private UserCreditAccount account(long userId) {
        UserCreditAccount account = accountMapper.selectById(userId);
        if (account != null) return account;
        account = new UserCreditAccount();
        account.setUserId(userId);
        account.setAvailableCredits(0);
        account.setFrozenCredits(0);
        account.setVersion(0L);
        account.setUpdatedAt(LocalDateTime.now());
        accountMapper.insert(account);
        return account;
    }

    private boolean isRecorded(String requestId) {
        return ledgerMapper.exists(Wrappers.<UserCreditLedger>lambdaQuery()
                .eq(UserCreditLedger::getRequestId, requestId));
    }

    private void updateAccount(UserCreditAccount account, int available, int frozen) {
        int changed = accountMapper.update(null, Wrappers.<UserCreditAccount>lambdaUpdate()
                .eq(UserCreditAccount::getUserId, account.getUserId())
                .eq(UserCreditAccount::getVersion, account.getVersion())
                .set(UserCreditAccount::getAvailableCredits, available)
                .set(UserCreditAccount::getFrozenCredits, frozen)
                .set(UserCreditAccount::getVersion, account.getVersion() + 1)
                .set(UserCreditAccount::getUpdatedAt, LocalDateTime.now()));
        if (changed != 1) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "Credit balance changed; retry request");
        }
    }

    private void ledger(CreditOperationDTO dto, String operation) {
        UserCreditLedger entry = new UserCreditLedger();
        entry.setId(SnowflakeIdUtil.next());
        entry.setUserId(dto.getUserId());
        entry.setOperation(operation);
        entry.setAmount(dto.getAmount());
        entry.setRequestId(dto.getRequestId());
        entry.setReason(dto.getReason());
        ledgerMapper.insert(entry);
    }
}
