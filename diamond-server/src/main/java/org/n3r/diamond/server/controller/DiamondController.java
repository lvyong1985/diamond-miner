package org.n3r.diamond.server.controller;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.n3r.diamond.server.service.DiamondService;
import org.n3r.diamond.server.service.DiskService;
import org.n3r.diamond.server.utils.Constants;
import org.n3r.diamond.server.utils.GlobalCounter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.LinkedList;
import java.util.List;


@Controller
public class DiamondController {

    @Autowired
    private DiamondService diamondService;

    @Autowired
    private DiskService diskService;


    public String getConfig(HttpServletRequest request, HttpServletResponse response, String dataId, String group) throws IOException {
        response.setHeader("Content-Type", "text/html;charset=UTF-8");
        final String address = getRemoteIP(request);
        if (address == null) {
            // 未找到远端地址，返回400错误
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return "400";
        }

        if (GlobalCounter.getCounter().decrementAndGet() >= 0) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return "503";
        }

        String md5 = this.diamondService.getContentMD5(dataId, group);
        if (md5 == null) {
            return "404";
        }

        response.setHeader(Constants.CONTENT_MD5, md5);

        // 禁用缓存
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
        response.setHeader("Cache-Control", "no-cache,no-store");

        File path = diskService.getDiamondFile(dataId, group);
        if (!path.exists() || !path.isFile()) return "404";

//        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH);
//        String lastModified = format.format(path.lastModified());
//        response.setHeader("Last-Modified", lastModified);
        response.setDateHeader("Last-Modified", path.lastModified());

        ServletOutputStream outputStream = response.getOutputStream();
        String content = FileUtils.readFileToString(path, Constants.ENCODING);
        IOUtils.write(content, outputStream);
        outputStream.close();


        return "OK";
    }


    public String getProbeModifyResult(HttpServletRequest request, HttpServletResponse response, String probeModify) {
        response.setHeader("Content-Type", "text/html;charset=UTF-8");
        final String address = getRemoteIP(request);
        if (address == null) {
            // 未找到远端地址，返回400错误
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return "400";
        }

        if (GlobalCounter.getCounter().decrementAndGet() >= 0) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return "503";
        }

        final List<ConfigKey> configKeyList = getConfigKeyList(probeModify);

        StringBuilder resultBuilder = new StringBuilder();
        for (ConfigKey key : configKeyList) {
            String md5 = this.diamondService.getContentMD5(key.getDataId(), key.getGroup());
            if (!StringUtils.equals(md5, key.getMd5())) {
                resultBuilder.append(key.getDataId()).append(Constants.WORD_SEPARATOR).append(key.getGroup())
                        .append(Constants.LINE_SEPARATOR);
            }
        }

        String returnHeader = resultBuilder.toString();
        try {
            returnHeader = URLEncoder.encode(resultBuilder.toString(), "UTF-8");
        } catch (Exception e) {
            // ignore
        }

        request.setAttribute("content", returnHeader);
        // 禁用缓存
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
        response.setHeader("Cache-Control", "no-cache,no-store");
        return "200";
    }


    public void setDiamondService(DiamondService diamondService) {
        this.diamondService = diamondService;
    }


    public void setDiskService(DiskService diskService) {
        this.diskService = diskService;
    }


    /**
     * 查找真实的IP地址
     *
     * @param request
     * @return
     */
    public String getRemoteIP(HttpServletRequest request) {
        if (request.getHeader("x-forwarded-for") == null) {
            return request.getRemoteAddr();
        }
        return request.getHeader("x-forwarded-for");
    }


    public static List<ConfigKey> getConfigKeyList(String configKeysString) {
        List<ConfigKey> configKeyList = new LinkedList<ConfigKey>();
        if (null == configKeysString || "".equals(configKeysString)) {
            return configKeyList;
        }
        String[] configKeyStrings = configKeysString.split(Constants.LINE_SEPARATOR);
        for (String configKeyString : configKeyStrings) {
            String[] configKey = configKeyString.split(Constants.WORD_SEPARATOR);
            if (configKey.length > 3) {
                continue;
            }
            ConfigKey key = new ConfigKey();
            if ("".equals(configKey[0])) {
                continue;
            }
            key.setDataId(configKey[0]);
            if (configKey.length >= 2 && !"".equals(configKey[1])) {
                key.setGroup(configKey[1]);
            }
            if (configKey.length == 3 && !"".equals(configKey[2])) {
                key.setMd5(configKey[2]);
            }
            configKeyList.add(key);
        }

        return configKeyList;
    }

    public static class ConfigKey {
        private String dataId;
        private String group;
        private String md5;


        public String getDataId() {
            return dataId;
        }


        public void setDataId(String dataId) {
            this.dataId = dataId;
        }


        public String getGroup() {
            return group;
        }


        public void setGroup(String group) {
            this.group = group;
        }


        public String getMd5() {
            return md5;
        }


        public void setMd5(String md5) {
            this.md5 = md5;
        }


        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("DataID: ").append(dataId).append("\r\n");
            sb.append("Group: ").append((null == group ? "" : group)).append("\r\n");
            sb.append("MD5: ").append((null == md5 ? "" : md5)).append("\r\n");
            return sb.toString();
        }
    }
}