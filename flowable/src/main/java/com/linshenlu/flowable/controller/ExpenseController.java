package com.linshenlu.flowable.controller;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.image.ProcessDiagramGenerator;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/expense") // 花费
public class ExpenseController {

	/**
	 * 工作流引擎 ProcessEngine对象，这是Activiti工作的核心。负责生成流程运行时的各种实例及数据、监控和管理流程的运行。
	 */
	@Autowired
	private ProcessEngine processEngine;
	@Autowired
	private RuntimeService runtimeService; // 与正在执行的流程实例和流程对象相关的service
	@Autowired
	private RepositoryService repositoryService;
	@Autowired
	private TaskService taskService;

	/**
	 *   部署流程定义
	 */
	@RequestMapping("/deployment")
	public String deploymentDefinition() {
		InputStream inputStreamBpm = this.getClass().getResourceAsStream("/flowable.cfg.xml");
		InputStream inputStreamPng = this.getClass().getResourceAsStream("/flowable.cfg.png");
		Deployment deployment = processEngine.getRepositoryService()// 与流程定义和部署相关的service
				.createDeployment()// 创建一个部署对象
				.name("出差报销")
				.addInputStream("flowable.bpmn", inputStreamBpm)// 从classpath的资源中加载，一次只能加载一个文件
				.addInputStream("flowable.png", inputStreamPng)
				.deploy();// 完成部署对象
		System.out.println("部署ID：" + deployment.getId());
		System.out.println("部署名称：" + deployment.getName());
		return "部署ID：" + deployment.getId() + "部署名称：" + deployment.getName();
	}
	/**
	 * 启动流程
	 * 通过接收用户的一个请求传入用户的ID和金额以及描述信息来开启一个报销流程，并返回给用户这个流程的Id
	 * 
	 * @param userId
	 * @param money
	 * @param descption
	 * @return
	 */
	@RequestMapping("/startExpenseProcess")
	public String startExpenseProcess(String userId, Integer money, String descption) {
		// 流程定义的key
		String processDefinitionKey = "Expense"; // 使用流程定义的key启动流程实例，key对应diagrams/approveProcess.bpmn中的id的属性值<process
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("taskUser", userId);
		map.put("money", money);
		map.put("descption", descption);
		// 根据流程定义key-->获取流程实例
		// startProcessInstanceByKey
		// startProcessInstanceById
		ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(processDefinitionKey, map);
		return "提交成功.流程实例Id为：" + processInstance.getId() + "流程实例定义id:" + processInstance.getProcessDefinitionId();
	}

	/**
	 * 根据用户ID，查询用户需要处理的流程
	 * 
	 * @param userId
	 * @return
	 */
	@RequestMapping("/listTask")
	public String listTask(String userId) {
		List<Task> list = taskService.createTaskQuery().taskAssignee(userId).orderByTaskCreateTime().desc().list();
		for (Task task : list) {
			System.out.println(task.toString());
		}
		return list.toArray().toString();
	}

	/**
	 * 批准，同意
	 * 
	 * @param taskId
	 * @return
	 */
	@RequestMapping("/approve")
	public String approve(String taskId) {

		Task singleResult = taskService.createTaskQuery().taskId(taskId).singleResult();
		if (singleResult == null) {
			throw new RuntimeException("流程不存在！");
		}
		// 通过审批
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("outcome", "通过");
		taskService.complete(taskId, map);
		return "processed ok!";
	}
	/**
	 * 修改流程数据
	 * @param map
	 * @return
	 */
	@RequestMapping("/modifyApprove")
	public String modifyApprove(@RequestBody Map map) {

		String taskId = (String)map.get("taskId");
		Task singleResult = taskService.createTaskQuery().taskId(taskId).singleResult();
		if (singleResult == null) {
			throw new RuntimeException("流程不存在！");
		}
		// 通过审批
		//Map<String, Object> map = new HashMap<String, Object>();
		//map.put("outcome", "通过");
		taskService.complete(taskId, map);
		return "processed ok!";
	}

	@RequestMapping("/reject")
	public String reject(String taskId) {

		Task singleResult = taskService.createTaskQuery().taskId(taskId).singleResult();
		if (singleResult == null) {
			throw new RuntimeException("流程不存在！");
		}

		Map<String, Object> map = new HashMap<String, Object>();
		map.put("outcome", "驳回");
		taskService.complete(taskId, map);
		return "reject!";
	}

	/**
	 * 
	 *      生成流程图
	 * @param processId 任务ID
	 * 
	 */
	@RequestMapping(value = "/processDiagram")
	public void genProcessDiagram(HttpServletResponse httpServletResponse, String processId) throws Exception {
		ProcessInstance pi = runtimeService.createProcessInstanceQuery().processInstanceId(processId).singleResult();
		// 流程走完的不显示图
		if (pi == null) {
			return;
		}
		Task task = taskService.createTaskQuery().processInstanceId(pi.getId()).singleResult();
		// 使用流程实例ID，查询正在执行的执行对象表，返回流程实例对象
		String InstanceId = task.getProcessInstanceId();
		List<Execution> executions = runtimeService
				.createExecutionQuery()
				.processInstanceId(InstanceId)
				.list();
		// 得到正在执行的Activity的Id
		List<String> activityIds = new ArrayList<>();
		List<String> flows = new ArrayList<>();
		for (Execution exe : executions) {
			List<String> ids = runtimeService.getActiveActivityIds(exe.getId());
			activityIds.addAll(ids);
		}
		// 获取流程图
		BpmnModel bpmnModel = repositoryService.getBpmnModel(pi.getProcessDefinitionId());
		ProcessEngineConfiguration engconf = processEngine.getProcessEngineConfiguration();
		ProcessDiagramGenerator diagramGenerator = engconf.getProcessDiagramGenerator();
		InputStream in = diagramGenerator.generateDiagram(bpmnModel, "png", activityIds, flows,
				engconf.getActivityFontName(), engconf.getLabelFontName(), engconf.getAnnotationFontName(),
				engconf.getClassLoader(), 1.0, false);
		OutputStream out = null;
		byte[] buf = new byte[1024];
		int legth = 0;
		try {
			out = httpServletResponse.getOutputStream();
			while ((legth = in.read(buf)) != -1) {
				out.write(buf, 0, legth);
			}
		} finally {
			if (in != null) {
				in.close();
			}
			if (out != null) {
				out.close();
			}
		}
	}
	//通过传入流程ID生成当前流程的流程图给前端,如果流程中使用到中文且生成的图片是乱码的，则需要进配置下字体：
	
}
