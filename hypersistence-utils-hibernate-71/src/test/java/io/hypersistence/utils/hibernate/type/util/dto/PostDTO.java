package io.hypersistence.utils.hibernate.type.util.dto;

/**
 * @author Vlad Mihalcea
 */
public class PostDTO {

	private Long id;

	private String title;

	public PostDTO(Number id, String title) {
		this.id = id.longValue();
		this.title = title;
	}

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}
}
