package com.pru.hk.util.velocity;

import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.tools.generic.DateTool;
import org.apache.velocity.tools.generic.EscapeTool;
import org.apache.velocity.tools.generic.NumberTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(BeanDefinition.SCOPE_SINGLETON)
public class VelocityUtil {
	private final Logger logger = Logger.getLogger("velocity");

	@Autowired
	private VelocityEngine ve = null;

	private VelocityContext initVelocityContext() {
		VelocityContext context = new VelocityContext();

		context.put("dateTool", new DateTool());
		// context.put("importTool", new ImportTool());
		context.put("importTool", new UTFImportTool());
		context.put("stringUtils", new StringUtils());
		context.put("escapeTool", new EscapeTool());
		context.put("numberTool", new NumberTool());
		context.put("htmlTool", new HtmlTool());
		context.put("localeTool", new LocaleTool());

		return context;
	}

	public String processTemplate(String templateName,
			Map<String, Object> dataMap, String encoding)
			throws UnsupportedEncodingException {
		VelocityContext context = initVelocityContext();

		StringWriter w = new StringWriter();

		if (dataMap != null && !dataMap.isEmpty()) {
			for (String key : dataMap.keySet()) {
				context.put(key, dataMap.get(key));
			}
		}

		ve.mergeTemplate(templateName, encoding, context, w);

		return w.toString();
	}
}
