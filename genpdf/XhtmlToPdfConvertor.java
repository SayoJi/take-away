package com.pru.hk.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.cxf.common.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.w3c.tidy.Tidy;
import org.xhtmlrenderer.pdf.ITextFontResolver;
import org.xhtmlrenderer.pdf.ITextRenderer;

import com.lowagie.text.DocumentException;
import com.lowagie.text.pdf.BaseFont;

/**
 * Created by npitlpsw on 27/04/2017.
 */
public class XhtmlToPdfConvertor {

    private static final String FONT_SETTINGS = "\n@font-face {\n" +
            "    font-family: Arial Unicode MS;\n" +
            "    src: local(\"%s\");\n" +
            "    -fs-pdf-font-encoding: Identity-H;\n" +
            "}\n\n* {\n    font-family: Arial Unicode MS;\n}\n" +
            "@page {\n" +
            "    size: A4 portrait;\n" +
            "    margin: 20px;\n" +
            "  @bottom-right {\n" +
            "    font-family: Arial Unicode MS;\n" +
            "    font-size : 0.8em;\n" +
            "    content: counter(page) \" of \" counter(pages);\n" +
            "  }\n" +
            "}";

    private String fontPath;

    public XhtmlToPdfConvertor(String fontPath){
        this.fontPath = fontPath;
    }

    private void addFontSetting(Document dom, String customFontSettings) {
        NodeList nodeList = dom.getElementsByTagName("head");
        if (nodeList != null && nodeList.getLength() > 0) {
            Element head = (Element) nodeList.item(0);

            customFontSettings = (StringUtils.isEmpty(customFontSettings) ? FONT_SETTINGS : customFontSettings);
            Text fontSetting = dom.createTextNode(String.format(customFontSettings, fontPath));
            Element style = dom.createElement("style");
            style.appendChild(fontSetting);
            head.appendChild(style);
        }
    }
    
    public void convert(InputStream io, OutputStream os) throws DocumentException, IOException {
        this.convert(io, os, "");
    }

    public void convert(InputStream io, OutputStream os, String customFontSettings)
            throws DocumentException, IOException {

        // load html to dom
        Tidy tidy = new Tidy();

        tidy.setInputEncoding("UTF-8");
        tidy.setOutputEncoding("UTF-8");
        tidy.setWraplen(Integer.MAX_VALUE);
        tidy.setTidyMark(true);
        tidy.setSmartIndent(true);
        tidy.setJoinClasses(true);
        tidy.setJoinStyles(true);
//        tidy.setFixUri(true);
        tidy.setXHTML(true);

        // parse dom
        Document dom = tidy.parseDOM(io, null);
        addFontSetting(dom, customFontSettings);

        // do render
        ITextRenderer renderer = new ITextRenderer();

        ITextFontResolver fontResolver = renderer.getFontResolver();

        fontResolver.addFont(fontPath, BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);

        renderer.setDocument(dom, "");
        renderer.layout();
        renderer.createPDF(os);
    }


}
