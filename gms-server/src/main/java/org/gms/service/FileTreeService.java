package org.gms.service;

import org.gms.util.I18nUtil;

import lombok.extern.slf4j.Slf4j;
import org.gms.exception.BizException;
import org.gms.model.dto.FileTreeNodeDTO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
@Service
public class FileTreeService {

    private static final String FILE_TREE_BASE_DIR = System.getProperty("user.dir");
    private static final Path FILE_TREE_BASE_DIR_PATH = Path.of(FILE_TREE_BASE_DIR);
    private static final String FILE_TREE_KEY_DELIMITER = "-";
    private static final Set<String> FILE_TREE_LIMITED_PATTERNS = new HashSet<>();

    private static final boolean FILE_TREE_PATH_STRICT_MODE = true;

    static {
        FILE_TREE_LIMITED_PATTERNS.add("scripts");
        FILE_TREE_LIMITED_PATTERNS.add("scripts-zh-CN");
        FILE_TREE_LIMITED_PATTERNS.add("wz");
        FILE_TREE_LIMITED_PATTERNS.add("wz-zh-CN");
    }

    public String readFile(String currentKey, String filename) {
        File file = resolveByTreeKey(currentKey);
        if (!filename.equals(file.getName())) throw new BizException("文件目录发生变动，请重新读取");
        try {
            return Files.readString(file.toPath(), UTF_8);
        } catch (MalformedInputException e) {
            log.error(I18nUtil.getLogMessage("Localization.legacy.log.85a00c0ba65647eb"), filename);
            try {
                return Files.readString(file.toPath(), ISO_8859_1);
            } catch (IOException ex) {
                log.error(I18nUtil.getLogMessage("Localization.legacy.log.85a7301ac3674d1b"), ex);
                throw new BizException("读取文件异常");
            }
        } catch (IOException e) {
            log.error(I18nUtil.getLogMessage("Localization.legacy.log.85a7301ac3674d1b"), e);
            throw new BizException("读取文件异常");
        }
    }

    public void writeFile(String currentKey, String filename, String content) {
        File file = resolveByTreeKey(currentKey);
        if (!filename.equals(file.getName())) throw new BizException("文件目录发生变动，请重新写入");
        try {
            Files.writeString(file.toPath(), content, UTF_8);
        } catch (IOException e) {
            log.error(I18nUtil.getLogMessage("Localization.legacy.log.85a7301ac3674d1b"), e);
            throw new BizException("读取文件异常");
        }
    }


    public List<FileTreeNodeDTO> tree(String currentKey) {
        // 入参为空表示在根目录
        boolean root = !StringUtils.hasText(currentKey);
        File current = root ? new File(FILE_TREE_BASE_DIR) : resolveByTreeKey(currentKey);

        if (!current.isDirectory()) throw new BizException("请输入文件夹");

        boolean parentIsMatch = matchAnyLimitPattern(current.toPath());

        File[] listFiles = current.listFiles();
        if (listFiles == null) return Collections.emptyList();

        List<FileTreeNodeDTO> nodes = new ArrayList<>();
        for (int i = 0; i < listFiles.length; i++) {
            File currentSubFile = listFiles[i];
            if (!parentIsMatch && !matchAnyLimitPattern(currentSubFile.toPath())) {
                continue;
            }
            String childKey = String.valueOf(i);
            String key = root ? childKey : String.join(FILE_TREE_KEY_DELIMITER, currentKey, childKey);
            nodes.add(new FileTreeNodeDTO(currentSubFile, key));
        }
        return nodes;
    }

    public File resolveByTreeKey(String currentKey) {
        @SuppressWarnings("UnnecessaryLocalVariable")
        File base = FILE_TREE_BASE_DIR_PATH.toFile();
        File current = base;
        String[] keyArray = currentKey.split(FILE_TREE_KEY_DELIMITER);
        try {
            for (String keyStr : keyArray) {
                int key = Integer.parseInt(keyStr);
                current = Objects.requireNonNull(current.listFiles())[key];
            }
        } catch (NullPointerException | NumberFormatException | ArrayIndexOutOfBoundsException e) {
            log.error(I18nUtil.getLogMessage("Localization.legacy.log.777b97af3951f61f"), e);
            throw new BizException("文件/文件夹不存在");
        }


        if (FILE_TREE_PATH_STRICT_MODE) {
            Path userPath = current.toPath().toAbsolutePath().normalize();
            if (!userPath.startsWith(FILE_TREE_BASE_DIR_PATH)) {
                log.error(I18nUtil.getLogMessage("Localization.legacy.log.943f97b7b413b1c8"), userPath);
                throw new BizException("检测到路径逃逸尝试");
            }

            if (!matchAnyLimitPattern(userPath)) {
                log.error(I18nUtil.getLogMessage("Localization.legacy.log.86a21806ff8041c2"), userPath);
                throw new BizException("检测到路径逃逸尝试");
            }
        }
        return current;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean matchAnyLimitPattern(Path path) {
        if (FILE_TREE_PATH_STRICT_MODE) {
            return FILE_TREE_LIMITED_PATTERNS.stream().anyMatch(it -> path.startsWith(FILE_TREE_BASE_DIR_PATH.resolve(it)));
        }
        return true;
    }

}

