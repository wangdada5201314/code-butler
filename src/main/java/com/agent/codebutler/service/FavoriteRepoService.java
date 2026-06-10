package com.agent.codebutler.service;

import com.agent.codebutler.dto.FavoriteRepoAddRequest;
import com.agent.codebutler.exception.BusinessException;
import com.agent.codebutler.exception.ErrorCode;
import com.agent.codebutler.mapper.FavoriteRepoMapper;
import com.agent.codebutler.model.entity.FavoriteRepo;
import com.agent.codebutler.model.vo.FavoriteRepoVO;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 收藏仓库服务
 */
@Service
public class FavoriteRepoService {

    private static final Logger log = LoggerFactory.getLogger(FavoriteRepoService.class);
    private static final int MAX_FAVORITES = 20;

    private final FavoriteRepoMapper favoriteRepoMapper;

    public FavoriteRepoService(FavoriteRepoMapper favoriteRepoMapper) {
        this.favoriteRepoMapper = favoriteRepoMapper;
    }

    /**
     * 获取用户所有收藏仓库（按创建时间倒序）
     */
    public List<FavoriteRepoVO> getUserFavorites(long userId) {
        QueryWrapper qw = QueryWrapper.create()
                .eq(FavoriteRepo::getUserId, userId)
                .orderBy(FavoriteRepo::getCreateTime, false);
        List<FavoriteRepo> repos = favoriteRepoMapper.selectListByQuery(qw);
        return repos.stream()
                .map(r -> FavoriteRepoVO.builder()
                        .id(r.getId())
                        .repoPath(r.getRepoPath())
                        .repoName(r.getRepoName())
                        .createTime(r.getCreateTime())
                        .build())
                .toList();
    }

    /**
     * 添加收藏仓库
     */
    public FavoriteRepoVO addFavorite(long userId, FavoriteRepoAddRequest req) {
        // 检查数量上限
        QueryWrapper countQw = QueryWrapper.create()
                .eq(FavoriteRepo::getUserId, userId);
        long count = favoriteRepoMapper.selectCountByQuery(countQw);
        if (count >= MAX_FAVORITES) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "收藏仓库数量已达上限（" + MAX_FAVORITES + "），请先删除部分仓库");
        }

        // 检查重复
        QueryWrapper dupQw = QueryWrapper.create()
                .eq(FavoriteRepo::getUserId, userId)
                .eq(FavoriteRepo::getRepoPath, req.getRepoPath());
        if (favoriteRepoMapper.selectOneByQuery(dupQw) != null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该仓库已在收藏列表中");
        }

        FavoriteRepo repo = FavoriteRepo.builder()
                .userId(userId)
                .repoPath(req.getRepoPath().trim())
                .repoName(req.getRepoName() != null ? req.getRepoName().trim() : "")
                .build();
        favoriteRepoMapper.insert(repo);
        log.info("收藏仓库已添加: userId={}, repoPath={}", userId, req.getRepoPath());

        return FavoriteRepoVO.builder()
                .id(repo.getId())
                .repoPath(repo.getRepoPath())
                .repoName(repo.getRepoName())
                .createTime(repo.getCreateTime())
                .build();
    }

    /**
     * 删除收藏仓库（验证所有权）
     */
    public void removeFavorite(long userId, long repoId) {
        QueryWrapper qw = QueryWrapper.create()
                .eq(FavoriteRepo::getId, repoId)
                .eq(FavoriteRepo::getUserId, userId);
        FavoriteRepo repo = favoriteRepoMapper.selectOneByQuery(qw);
        if (repo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "收藏记录不存在");
        }
        favoriteRepoMapper.deleteById(repoId);
        log.info("收藏仓库已删除: userId={}, repoId={}", userId, repoId);
    }
}
