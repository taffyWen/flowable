package com.linshenlu.flowable.handler;

import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;

/**
 *      老板审批
 * @author wen
 * @date 2019年10月26日 下午3:45:30
 */
public class BossTaskHandler implements TaskListener {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	public void notify(DelegateTask delegateTask) {
		// TODO Auto-generated method stub
		String id = delegateTask.getId();
		System.out.println(id);
		
		delegateTask.setAssignee("老板");
	}

}
