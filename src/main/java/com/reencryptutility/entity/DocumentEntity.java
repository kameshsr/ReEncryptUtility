/* 
 * Copyright
 * 
 */
package com.reencryptutility.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * This entity class defines the database table details for Document.

 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "applicant_document", schema = "prereg")
public class DocumentEntity implements Serializable {

	/** The Constant serialVersionUID. */
	private static final long serialVersionUID = 1692781286748263575L;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "prereg_id", nullable = false)
	private DemographicEntity demographicEntity;

	/**
	 * Document Id
	 */

	@Id
	@Column(name = "id")
	private String documentId;

	// /**
	// * PreRegistration Id
	// */
	// @Column(name = "prereg_id")
	// private String preregId;

	/**
	 * Document Name
	 */
	@Column(name = "doc_name")
	private String docName;

	/**
	 * Document Category
	 */
	@Column(name = "doc_cat_code")
	private String docCatCode;

	/**
	 * Document Type
	 */
	@Column(name = "doc_typ_code")
	private String docTypeCode;

	/**
	 * Document File Format
	 */
	@Column(name = "doc_file_format")
	private String docFileFormat;

	/**
	 * Status Code
	 */
	@Column(name = "status_code")
	private String statusCode;

	/**
	 * Language Code
	 */
	@Column(name = "lang_code")
	private String langCode;

	/**
	 * Created By
	 */
	@Column(name = "cr_by")
	private String crBy;

	/**
	 * Created Date Time
	 */
	@Column(name = "cr_dtimes")
	private LocalDateTime crDtime;

	/**
	 * Updated By
	 */
	@Column(name = "upd_by")
	private String updBy;

	/**
	 * Updated Date Time
	 */
	@Column(name = "upd_dtimes")
	private LocalDateTime updDtime;

	/**
	 * Encrypted Date Time
	 */
	@Column(name = "encrypted_dtimes")
	private LocalDateTime encryptedDateTime;

	/**
	 * Document Id
	 */
	@Column(name = "doc_id")
	private String docId;

	/**
	 * Hash value of row
	 */
	@Column(name = "doc_hash")
	private String DocHash;
	
	/**
	 * Hash value of row
	 */
	@Column(name = "doc_ref_id")
	private String refNumber;


}
