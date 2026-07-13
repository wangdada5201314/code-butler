package com.agent.codebutler.service;

import com.agent.codebutler.mapper.CodeKnowledgeMapper;
import com.agent.codebutler.model.entity.CodeKnowledgeEntity;
import com.agent.codebutler.util.VectorUtils;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.agent.codebutler.model.entity.table.CodeKnowledgeEntityTableDef.CODE_KNOWLEDGE_ENTITY;

/**
 * 代码知识库数据访问层
 * <p>
 * 封装 CodeKnowledgeMapper 的数据库操作，
 * 包括加载、保存、删除和统计，以及向量序列化/反序列化。
 */
@Component
public class CodeKnowledgeRepository {

    private final CodeKnowledgeMapper codeKnowledgeMapper;

    public CodeKnowledgeRepository(CodeKnowledgeMapper codeKnowledgeMapper) {
        this.codeKnowledgeMapper = codeKnowledgeMapper;
    }

    /**
     * 从数据库加载仓库的所有知识片段（含向量反序列化）
     */
    public List<CodeKnowledgeService.KnowledgeChunk> loadFromDatabase(String repoPath) {
        List<CodeKnowledgeEntity> entities = codeKnowledgeMapper.selectListByQuery(
                QueryWrapper.create().where(CODE_KNOWLEDGE_ENTITY.REPO_PATH.eq(repoPath)));

        List<CodeKnowledgeService.KnowledgeChunk> chunks = new ArrayList<>();
        for (CodeKnowledgeEntity entity : entities) {
            double[] embedding = VectorUtils.jsonToVector(entity.getEmbedding());
            chunks.add(new CodeKnowledgeService.KnowledgeChunk(
                    entity.getId(), entity.getFilePath(), entity.getChunkId(),
                    entity.getContent(), entity.getLanguage(), embedding));
        }
        return chunks;
    }

    /**
     * 批量保存知识片段到数据库（含向量序列化）
     *
     * @return 保存后的 KnowledgeChunk 列表（含数据库生成的 ID）
     */
    public List<CodeKnowledgeService.KnowledgeChunk> saveChunks(
            String repoPath,
            List<CodeChunker.CodeChunk> chunks,
            List<double[]> embeddings) {

        List<CodeKnowledgeService.KnowledgeChunk> result = new ArrayList<>();

        for (int i = 0; i < chunks.size() && i < embeddings.size(); i++) {
            CodeChunker.CodeChunk chunk = chunks.get(i);
            double[] embedding = embeddings.get(i);

            CodeKnowledgeEntity entity = CodeKnowledgeEntity.builder()
                    .repoPath(repoPath)
                    .filePath(chunk.filePath())
                    .chunkId(chunk.chunkId())
                    .content(chunk.content())
                    .language(chunk.language())
                    .embedding(VectorUtils.vectorToJson(embedding))
                    .build();
            codeKnowledgeMapper.insert(entity);

            result.add(new CodeKnowledgeService.KnowledgeChunk(
                    entity.getId(), chunk.filePath(), chunk.chunkId(),
                    chunk.content(), chunk.language(), embedding));
        }

        return result;
    }

    /**
     * 删除仓库的所有索引数据
     */
    public void deleteByRepoPath(String repoPath) {
        codeKnowledgeMapper.deleteByQuery(
                QueryWrapper.create().where(CODE_KNOWLEDGE_ENTITY.REPO_PATH.eq(repoPath)));
    }

    /**
     * 查询仓库在数据库中的索引片段数
     */
    public int getChunkCount(String repoPath) {
        return (int) codeKnowledgeMapper.selectCountByQuery(
                QueryWrapper.create().where(CODE_KNOWLEDGE_ENTITY.REPO_PATH.eq(repoPath)));
    }

    /**
     * 统计仓库在数据库中的去重文件数
     */
    public int countDistinctFiles(String repoPath) {
        List<CodeKnowledgeEntity> entities = codeKnowledgeMapper.selectListByQuery(
                QueryWrapper.create()
                        .select(CODE_KNOWLEDGE_ENTITY.FILE_PATH)
                        .where(CODE_KNOWLEDGE_ENTITY.REPO_PATH.eq(repoPath))
                        .groupBy(CODE_KNOWLEDGE_ENTITY.FILE_PATH));
        return entities.size();
    }
}
