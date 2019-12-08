package com.vladmihalcea.hibernate.type.util;

import org.hibernate.boot.Metadata;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.service.spi.SessionFactoryServiceRegistry;

import java.util.List;

/**
 * The {@link ClassImportIntegrator} implements the Hibernate {@link Integrator} contract
 * and allows you to provide a {@link List} of classes to be imported using their simple name.
 *
 * For instance, you could use a DTO simple class name, instead of the fully-qualified name
 * when building a constructor expression in a JPQL query.
 *
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/dto-projection-jpa-query/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 */
public class ClassImportIntegrator implements Integrator {

	private final List<? extends Class> classImportList;

	/**
	 * Builds a new integrator that can register the provided classes.
	 *
	 * @param classImportList list of classes to be imported
	 */
	public ClassImportIntegrator(List<? extends Class> classImportList) {
		this.classImportList = classImportList;
	}

	/**
	 * Register the provided classes by their simple name.
	 *
	 * @param metadata metadata
	 * @param sessionFactory Hibernate session factory
	 * @param serviceRegistry Hibernate service registry
	 */
	@Override
	public void integrate(
			Metadata metadata,
			SessionFactoryImplementor sessionFactory,
			SessionFactoryServiceRegistry serviceRegistry) {
		for(Class classImport : classImportList) {
			metadata.getImports().put(
				classImport.getSimpleName(),
				classImport.getName()
			);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void disintegrate(
			SessionFactoryImplementor sessionFactory,
			SessionFactoryServiceRegistry serviceRegistry) {}
}