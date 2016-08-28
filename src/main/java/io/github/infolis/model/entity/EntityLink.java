package io.github.infolis.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.infolis.model.BaseModel;

import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 * @author domi
 */
@XmlRootElement(name = "link")
@XmlAccessorType(XmlAccessType.FIELD)
@JsonIgnoreProperties(ignoreUnknown = true)
public class EntityLink extends BaseModel {

    private String toEntity;
    private String fromEntity;
    private double confidence;
    private String linkReason;
    private Set<EntityRelation> entityRelations;
    
    public EntityLink() {
    }

	public EntityLink(String fromEntity, String toEntity, double confidence, String linkReason)
	{
		this.fromEntity = fromEntity;
		this.toEntity = toEntity;
		this.confidence = confidence;
		this.linkReason = linkReason;
		this.entityRelations = new HashSet<>();
	}
	
	public EntityLink(String fromEntity, String toEntity, double confidence, String linkReason, Set<EntityRelation> entityRelations)
	{
		this.fromEntity = fromEntity;
		this.toEntity = toEntity;
		this.confidence = confidence;
		this.linkReason = linkReason;
		this.entityRelations = entityRelations;
	}
	
	public enum EntityRelation {
		part_of_temporal,
	    part_of_spatial,
	    parts_of_temporal,
	    parts_of_spatial,
	    superset_of_temporal,
	    superset_of_spatial,
	    version_of,
	    part_of_confidential,
	    part_of_sample,
	    part_of_supplement,
	    part_of,
	    same_as_temporal,
	    same_as_spatial,
	    unknown
	};
	
	public void setEntityRelations(Set<EntityRelation> entityRelations) {
		this.entityRelations = entityRelations;
	}
	
	public void addEntityRelation(EntityRelation entityRelation) {
		this.entityRelations.add(entityRelation);
	}
	
	public Set<EntityRelation> getEntityRelations() {
		return this.entityRelations;
	}

	public String getToEntity()
	{
		return toEntity;
	}

	public void setToEntity(String toEntity)
	{
		this.toEntity = toEntity;
	}

	public String getFromEntity()
	{
		return fromEntity;
	}

	public void setFromEntity(String fromEntity)
	{
		this.fromEntity = fromEntity;
	}

    /**
     * @return the confidence
     */
    public double getConfidence() {
        return confidence;
    }

    /**
     * @param confidence the confidence to set
     */
    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    /**
     * @return the linkReason
     */
    public String getLinkReason() {
        return linkReason;
    }

    /**
     * @param linkReason the linkReason to set
     */
    public void setLinkReason(String linkReason) {
        this.linkReason = linkReason;
    }

}
