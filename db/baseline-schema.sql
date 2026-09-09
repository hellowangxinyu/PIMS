-- PIMS 基线建表脚本（v8.9 自动生成，勿手改——实体变更后删除本文件再跑 mvn test 刷新）
-- 91 张实体表（Hibernate 按 @Entity 生成）；非实体表（stat_*/操作日志月表等）由启动 Initializer 幂等补建
-- 空库自举：sqlite3 pims.db < db/baseline-schema.sql 后启动 jar 即完成全部初始化


    create table account_mapping (
        id integer,
        update_time timestamp,
        subject_code varchar(20) not null,
        map_key varchar(60) not null unique,
        remark varchar(200),
        primary key (id)
    );

    create table account_period (
        closed boolean not null,
        close_time timestamp,
        id integer,
        period varchar(10) not null unique,
        closed_by varchar(50),
        primary key (id)
    );

    create table account_subject (
        opening_balance numeric(14,2),
        direction varchar(5) not null,
        opening_direction varchar(5),
        create_time timestamp,
        id integer,
        update_time timestamp,
        category varchar(20) not null,
        code varchar(20) not null unique,
        parent_code varchar(20),
        status varchar(20) not null,
        name varchar(100) not null,
        primary key (id)
    );

    create table accounts_payable (
        amount numeric(14,2) not null,
        due_date date,
        paid_amount numeric(14,2),
        arrival_id bigint,
        create_time timestamp,
        id integer,
        supplier_id bigint not null,
        update_time timestamp,
        doc_no varchar(20) not null unique,
        outsource_order_no varchar(20),
        payable_type varchar(20) not null,
        purchase_order_no varchar(20),
        status varchar(20) not null,
        remark varchar(500),
        primary key (id)
    );

    create table accounts_receivable (
        amount numeric(14,2) not null,
        due_date date,
        received_amount numeric(14,2),
        create_time timestamp,
        customer_id bigint not null,
        id integer,
        update_time timestamp,
        contract_no varchar(20),
        doc_no varchar(20) not null unique,
        sales_order_no varchar(20),
        status varchar(20) not null,
        remark varchar(500),
        primary key (id)
    );

    create table advance_payment (
        amount numeric(14,2) not null,
        pay_date date,
        used_amount numeric(14,2) not null,
        create_time timestamp,
        id integer,
        partner_id bigint,
        update_time timestamp,
        direction varchar(20) not null,
        doc_no varchar(20) not null unique,
        method varchar(20),
        status varchar(20) not null,
        created_by varchar(50),
        partner_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table asset (
        original_value numeric(14,2) not null,
        purchase_date date,
        residual_rate numeric(5,2),
        scrap_date date,
        useful_life_months integer not null,
        create_time timestamp,
        id integer,
        update_time timestamp,
        category varchar(20) not null,
        doc_no varchar(20) not null unique,
        expense_subject varchar(20) not null,
        status varchar(20) not null,
        keeper varchar(50),
        location varchar(100),
        name varchar(100) not null,
        remark varchar(500),
        primary key (id)
    );

    create table asset_depreciation (
        amount numeric(14,2) not null,
        asset_id bigint not null,
        create_time timestamp,
        id integer,
        voucher_id bigint,
        period varchar(10) not null,
        expense_subject varchar(20) not null,
        asset_name varchar(100) not null,
        primary key (id)
    );

    create table bank_account (
        opening_balance numeric(16,2),
        create_time timestamp,
        id integer,
        enabled varchar(10),
        account_no varchar(30),
        name varchar(50) not null,
        bank_name varchar(100),
        primary key (id)
    );

    create table bank_statement (
        amount numeric(16,2) not null,
        balance numeric(16,2),
        tx_date date not null,
        account_id bigint not null,
        create_time timestamp,
        id integer,
        ref_id bigint,
        status varchar(10),
        ref_type varchar(20),
        import_batch varchar(30),
        counterparty varchar(100),
        summary varchar(200),
        primary key (id)
    );

    create table coding_rule (
        current_seq integer not null,
        enabled boolean,
        number_start integer not null,
        create_time timestamp,
        id integer,
        update_time timestamp,
        category_code varchar(10) not null,
        sub_category_code varchar(10) not null,
        category varchar(50) not null,
        sub_category varchar(50) not null,
        primary key (id)
    );

    create table crm_contact (
        is_primary boolean,
        create_time timestamp,
        customer_id bigint,
        id integer,
        update_time timestamp,
        phone varchar(30),
        name varchar(50) not null,
        title varchar(50),
        wechat varchar(50),
        company_name varchar(100) not null,
        email varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table crm_follow_up (
        follow_date date,
        next_date date,
        create_time timestamp,
        customer_id bigint,
        id integer,
        opportunity_id bigint,
        method varchar(20) not null,
        operator varchar(50),
        content varchar(2000) not null,
        primary key (id)
    );

    create table crm_opportunity (
        expect_amount numeric(14,2),
        expect_date date,
        create_time timestamp,
        customer_id bigint,
        id integer,
        update_time timestamp,
        stage varchar(20) not null,
        won_order_no varchar(20),
        created_by varchar(50),
        owner varchar(50),
        company_name varchar(100) not null,
        title varchar(100) not null,
        loss_reason varchar(200),
        product_interest varchar(200),
        remark varchar(500),
        primary key (id)
    );

    create table customer (
        blacklisted boolean,
        credit_limit numeric(14,2),
        enabled boolean,
        create_time timestamp,
        id integer,
        update_time timestamp,
        abc_level varchar(10),
        code varchar(20) not null unique,
        contact_phone varchar(20),
        payment_method varchar(20),
        tax_no varchar(30),
        bank_account varchar(50),
        contact_person varchar(50),
        legal_person varchar(50),
        bank_name varchar(100),
        name varchar(100) not null,
        address varchar(200),
        payment_terms varchar(500),
        remark varchar(500),
        primary key (id)
    );

    create table customer_complaint (
        close_date date,
        complaint_date date,
        resolve_date date,
        create_time timestamp,
        customer_id bigint,
        id integer,
        update_time timestamp,
        category varchar(20),
        complaint_no varchar(20) not null unique,
        qc_doc_no varchar(20),
        sales_order_no varchar(20),
        status varchar(20) not null,
        batch_no varchar(30),
        material_code varchar(30),
        created_by varchar(50),
        handler varchar(50),
        customer_name varchar(100) not null,
        material_name varchar(100),
        remark varchar(500),
        action varchar(1000),
        cause varchar(1000),
        description varchar(2000) not null,
        primary key (id)
    );

    create table dict_item (
        enabled boolean,
        sort_order integer,
        create_time timestamp,
        id integer,
        type varchar(50) not null,
        value varchar(100) not null,
        label varchar(200),
        primary key (id)
    );

    create table employee (
        base_salary numeric(12,2),
        hire_date date,
        leave_date date,
        create_time timestamp,
        id integer,
        update_time timestamp,
        dept varchar(20) not null,
        status varchar(20) not null,
        phone varchar(30),
        bank_card varchar(50),
        name varchar(50) not null,
        position varchar(50),
        remark varchar(500),
        primary key (id)
    );

    create table expense (
        amount numeric(14,2) not null,
        occur_date date,
        create_time timestamp,
        id integer,
        update_time timestamp,
        direction varchar(20) not null,
        doc_no varchar(20) not null unique,
        method varchar(20),
        created_by varchar(50),
        expense_type varchar(50) not null,
        handler varchar(50),
        partner varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table finished_product_purchase (
        is_free boolean,
        purchase_date date,
        qty numeric(14,3),
        received_qty numeric(14,3),
        tax_rate numeric(5,2),
        total_amount numeric(14,2),
        unit_price numeric(12,2),
        create_time timestamp,
        id integer,
        supplier_id bigint,
        update_time timestamp,
        status varchar(20),
        material_code varchar(30),
        order_no varchar(30) not null unique,
        warehouse_id varchar(30),
        brand varchar(50),
        created_by varchar(50),
        material_name varchar(100),
        supplier_name varchar(100),
        file_path varchar(200),
        remark varchar(500),
        primary key (id)
    );

    create table inventory_ledger (
        amount numeric(14,2),
        available_qty numeric(14,3),
        expiry_date date,
        in_transit_qty numeric(14,3),
        inbound_date date,
        occupied_qty numeric(14,3),
        produce_date date,
        qc_date date,
        qty numeric(14,3),
        unit_price numeric(14,2),
        create_time timestamp,
        id integer,
        last_update_time timestamp,
        unit varchar(10),
        location_id varchar(20),
        ownership_type varchar(20) not null,
        qc_status varchar(20),
        warehouse_id varchar(20) not null,
        batch_no varchar(30),
        material_code varchar(30) not null,
        qc_inspection_no varchar(30),
        location_name varchar(50),
        qc_inspector varchar(50),
        zone_name varchar(50),
        material_name varchar(100),
        qc_result varchar(500),
        primary key (id)
    );

    create table inventory_movement (
        qty numeric(14,3) not null,
        qty_after numeric(14,3),
        qty_before numeric(14,3),
        direction varchar(5) not null,
        create_time timestamp,
        id integer,
        doc_type varchar(20) not null,
        location_id varchar(20),
        ownership_type varchar(20) not null,
        warehouse_id varchar(20) not null,
        batch_no varchar(30),
        doc_no varchar(30) not null,
        material_code varchar(30) not null,
        operator varchar(50),
        remark varchar(500),
        primary key (id)
    );

    create table invoice (
        amount numeric(14,2) not null,
        invoice_date date,
        tax_amount numeric(14,2),
        tax_rate integer,
        total_amount numeric(14,2),
        create_time timestamp,
        id integer,
        partner_id bigint,
        update_time timestamp,
        direction varchar(20) not null,
        doc_no varchar(20) not null unique,
        flush_doc_no varchar(20),
        partner_type varchar(20) not null,
        status varchar(20) not null,
        invoice_no varchar(30),
        partner_tax_no varchar(30),
        ref_order_no varchar(30),
        created_by varchar(50),
        partner_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table loss_letter_template (
        enabled boolean not null,
        is_default boolean not null,
        create_time timestamp,
        id integer,
        update_time timestamp,
        name varchar(50) not null,
        closing_text varchar(1000),
        opening_text varchar(1000),
        require_text varchar(1000),
        body_text varchar(2000) not null,
        primary key (id)
    );

    create table material (
        enabled boolean,
        shelf_life_days integer,
        create_time timestamp,
        id integer,
        update_time timestamp,
        color_series varchar(20),
        main_material varchar(20),
        code varchar(30) not null unique,
        brand varchar(50),
        category varchar(50),
        sub_category varchar(50),
        brand_owner varchar(100),
        name varchar(100) not null,
        alternative_codes varchar(500),
        primary key (id)
    );

    create table other_inbound (
        finance_amount numeric(14,2),
        gen_finance boolean,
        price numeric(12,4),
        qty numeric(14,3) not null,
        return_amount numeric(14,2),
        tax_rate numeric(5,2),
        create_time timestamp,
        customer_id bigint,
        finance_partner_id bigint,
        id integer,
        return_ref_id bigint,
        supplier_id bigint,
        update_time timestamp,
        unit varchar(10),
        doc_no varchar(20) not null unique,
        finance_doc_no varchar(20),
        location_id varchar(20),
        reason varchar(20),
        return_offset_status varchar(20),
        return_ref_type varchar(20),
        status varchar(20) not null,
        warehouse_id varchar(20) not null,
        batch_no varchar(30),
        material_code varchar(30) not null,
        return_ref_doc_no varchar(30),
        created_by varchar(50),
        finance_partner_name varchar(50),
        material_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table other_outbound (
        cost numeric(14,2),
        finance_amount numeric(14,2),
        gen_finance boolean,
        qty numeric(14,3) not null,
        unit_price numeric(14,2),
        create_time timestamp,
        finance_partner_id bigint,
        id integer,
        return_order_id bigint,
        update_time timestamp,
        unit varchar(10),
        doc_no varchar(20) not null unique,
        finance_doc_no varchar(20),
        location_id varchar(20),
        reason varchar(20),
        return_ref_doc_no varchar(20),
        status varchar(20) not null,
        warehouse_id varchar(20) not null,
        batch_no varchar(30),
        material_code varchar(30) not null,
        created_by varchar(50),
        finance_partner_name varchar(50),
        material_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table outsource_finish_inbound (
        qty numeric(14,3) not null,
        theoretical_qty numeric(14,3),
        yield_rate numeric(8,2),
        create_time timestamp,
        id integer,
        update_time timestamp,
        unit varchar(10),
        doc_no varchar(20) not null unique,
        location_id varchar(20),
        outsource_order_no varchar(20) not null,
        status varchar(20) not null,
        warehouse_id varchar(20) not null,
        batch_no varchar(30),
        created_by varchar(50),
        location_name varchar(50),
        product_code varchar(50),
        zone_name varchar(50),
        product_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table outsource_material_consume (
        consume_qty numeric(14,3) not null,
        remain_qty numeric(14,3),
        id integer,
        unit varchar(10),
        inbound_doc_no varchar(20) not null,
        outsource_order_no varchar(20) not null,
        batch_no varchar(30),
        material_code varchar(30) not null,
        remark varchar(200),
        primary key (id)
    );

    create table outsource_material_outbound (
        cost numeric(14,2),
        qty numeric(14,3) not null,
        signed_diff numeric(14,3),
        signed_qty numeric(14,3),
        unit_price numeric(14,2),
        create_time timestamp,
        id integer,
        update_time timestamp,
        unit varchar(10),
        doc_no varchar(20) not null unique,
        from_warehouse_id varchar(20) not null,
        location_id varchar(20),
        outsource_order_no varchar(20) not null,
        processor_id varchar(20) not null,
        status varchar(20) not null,
        to_warehouse_id varchar(20),
        batch_no varchar(30),
        material_code varchar(30) not null,
        created_by varchar(50),
        location_name varchar(50),
        zone_name varchar(50),
        processor_name varchar(100),
        signed_diff_reason varchar(200),
        remark varchar(500),
        primary key (id)
    );

    create table outsource_order (
        batch_qty numeric(14,3) not null,
        print_count integer not null,
        processing_fee numeric(12,2),
        schedule_seq integer,
        create_time timestamp,
        id integer,
        recipe_version_id bigint,
        supplier_id bigint,
        update_time timestamp,
        unit varchar(10),
        order_no varchar(20) not null unique,
        sales_order_no varchar(20),
        status varchar(20) not null,
        created_by varchar(50),
        product_code varchar(50),
        processor varchar(100),
        product_name varchar(100) not null,
        remark varchar(500),
        primary key (id)
    );

    create table outsource_order_item (
        qty numeric(14,3) not null,
        id integer,
        order_id bigint not null,
        ref_recipe_id bigint,
        unit varchar(10),
        node_type varchar(20),
        material_code varchar(30) not null,
        material_name varchar(100),
        spec varchar(100),
        remark varchar(200),
        primary key (id)
    );

    create table packaging_standard (
        capacity_kg numeric(10,3),
        enabled boolean,
        unit_price numeric(14,2) not null,
        create_time timestamp,
        id integer,
        update_time timestamp,
        pack_type varchar(20) not null,
        name varchar(100) not null,
        spec varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table packaging_standard_item (
        qty numeric(10,3) not null,
        sort_order integer,
        unit_price numeric(14,2) not null,
        id integer,
        packaging_id bigint not null,
        pack_type varchar(20),
        name varchar(100) not null,
        spec varchar(100),
        primary key (id)
    );

    create table payment_disbursement (
        amount numeric(14,2) not null,
        pay_date date not null,
        ap_id bigint,
        create_time timestamp,
        id integer,
        supplier_id bigint not null,
        ap_doc_no varchar(20),
        doc_no varchar(20) not null unique,
        method varchar(20) not null,
        bank_account varchar(50),
        operator varchar(50),
        supplier_name varchar(50),
        remark varchar(500),
        primary key (id)
    );

    create table payment_receipt (
        amount numeric(14,2) not null,
        receipt_date date not null,
        ar_id bigint,
        create_time timestamp,
        customer_id bigint not null,
        id integer,
        ar_doc_no varchar(20),
        doc_no varchar(20) not null unique,
        method varchar(20) not null,
        bank_account varchar(50),
        customer_name varchar(50),
        operator varchar(50),
        remark varchar(500),
        primary key (id)
    );

    create table price_policy (
        effective_date date not null,
        expiry_date date,
        min_qty numeric(14,3),
        unit_price numeric(14,2) not null,
        material_category varchar(5),
        create_time timestamp,
        id integer,
        status varchar(10),
        material_code varchar(30),
        created_by varchar(50),
        remark varchar(200),
        primary key (id)
    );

    create table process_qc_item (
        sort_order integer,
        test_times integer,
        id integer,
        stage_id bigint not null,
        unit varchar(20),
        name varchar(50) not null,
        standard varchar(100),
        method varchar(200),
        primary key (id)
    );

    create table process_stage (
        sort_order integer,
        id integer,
        template_id bigint not null,
        stage_no varchar(10),
        role_hint varchar(50),
        stage_name varchar(50) not null,
        primary key (id)
    );

    create table process_step (
        sort_order integer,
        id integer,
        stage_id bigint not null,
        step_code varchar(10),
        params varchar(200),
        description varchar(1000),
        primary key (id)
    );

    create table process_template (
        enabled boolean,
        is_default boolean not null,
        create_time timestamp,
        id integer,
        update_time timestamp,
        recipe_type varchar(20) not null,
        created_by varchar(50),
        name varchar(50) not null,
        packing_requirement varchar(1000),
        primary key (id)
    );

    create table production_inbound (
        qty numeric(14,3) not null,
        theoretical_qty numeric(14,3),
        yield_rate numeric(8,2),
        create_time timestamp,
        id integer,
        update_time timestamp,
        unit varchar(10),
        doc_no varchar(20) not null unique,
        location_id varchar(20),
        production_order_no varchar(20),
        status varchar(20) not null,
        warehouse_id varchar(20) not null,
        batch_no varchar(30),
        created_by varchar(50),
        location_name varchar(50),
        product_code varchar(50),
        zone_name varchar(50),
        product_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table production_order (
        batch_qty numeric(14,3) not null,
        print_count integer not null,
        schedule_seq integer,
        create_time timestamp,
        id integer,
        recipe_version_id bigint,
        update_time timestamp,
        unit varchar(10),
        order_no varchar(20) not null unique,
        sales_order_no varchar(20),
        status varchar(20) not null,
        created_by varchar(50),
        product_code varchar(50),
        product_name varchar(100) not null,
        remark varchar(500),
        primary key (id)
    );

    create table production_order_exception (
        input_qty numeric(14,3),
        io_ratio numeric(8,2),
        output_qty numeric(14,3),
        closed_time timestamp,
        create_time timestamp,
        id integer,
        update_time timestamp,
        order_no varchar(20) not null unique,
        status varchar(20) not null,
        created_by varchar(50),
        handler varchar(50),
        reason varchar(200),
        measure varchar(500),
        remark varchar(500),
        primary key (id)
    );

    create table production_order_item (
        qty numeric(14,3) not null,
        id integer,
        order_id bigint not null,
        ref_recipe_id bigint,
        unit varchar(10),
        node_type varchar(20),
        material_code varchar(30) not null,
        material_name varchar(100),
        spec varchar(100),
        remark varchar(200),
        primary key (id)
    );

    create table production_outbound (
        cost numeric(14,2),
        qty numeric(14,3) not null,
        unit_price numeric(14,2),
        create_time timestamp,
        id integer,
        update_time timestamp,
        unit varchar(10),
        doc_no varchar(20) not null unique,
        doc_type varchar(20) not null,
        location_id varchar(20),
        production_order_no varchar(20),
        status varchar(20) not null,
        supplement_type varchar(20),
        warehouse_id varchar(20) not null,
        batch_no varchar(30),
        material_code varchar(30) not null,
        created_by varchar(50),
        location_name varchar(50),
        zone_name varchar(50),
        material_name varchar(100),
        product_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table purchase_arrival (
        arrival_date date,
        qty numeric(14,3),
        tax_rate numeric(5,2),
        unit_price numeric(14,4),
        create_time timestamp,
        id integer,
        supplier_id bigint,
        type varchar(10) not null,
        unit varchar(10),
        location_id varchar(20),
        status varchar(20) not null,
        warehouse_id varchar(20),
        batch_no varchar(30),
        doc_no varchar(30),
        material_code varchar(30),
        ref_order_no varchar(30) not null,
        location_name varchar(50),
        zone_name varchar(50),
        material_name varchar(100),
        operator varchar(100),
        supplier_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table purchase_order (
        expected_delivery_date date,
        order_date date,
        total_amount numeric(14,2),
        create_time timestamp,
        id integer,
        supplier_id bigint,
        update_time timestamp,
        order_no varchar(20) not null unique,
        status varchar(20) not null,
        target_warehouse_id varchar(20),
        created_by varchar(50),
        payment_terms varchar(200),
        remark varchar(500),
        primary key (id)
    );

    create table purchase_order_item (
        amount numeric(14,2),
        qty numeric(14,3) not null,
        received_qty numeric(14,3),
        return_qty numeric(14,3),
        unit_price numeric(12,2),
        id integer,
        order_id bigint not null,
        unit varchar(10),
        material_code varchar(30) not null,
        remark varchar(500),
        primary key (id)
    );

    create table qc_template (
        enabled boolean,
        is_default boolean,
        create_time timestamp,
        id integer,
        update_time timestamp,
        apply_category varchar(10) not null,
        color_series varchar(20),
        main_material varchar(20),
        created_by varchar(50),
        sub_category varchar(50),
        name varchar(100) not null,
        remark varchar(500),
        primary key (id)
    );

    create table qc_template_item (
        sort_order integer,
        id integer,
        template_id bigint not null,
        unit varchar(30),
        name varchar(100) not null,
        method varchar(200),
        standard varchar(200),
        primary key (id)
    );

    create table quality_inspection (
        inspect_date date,
        print_count integer not null,
        produce_date date,
        qty numeric(14,3),
        unit_price numeric(14,2),
        material_category varchar(5),
        arrival_id bigint,
        create_time timestamp,
        id integer,
        update_time timestamp,
        unit varchar(10),
        location_id varchar(20),
        status varchar(20) not null,
        type varchar(20) not null,
        warehouse_id varchar(20),
        batch_no varchar(30),
        inspection_no varchar(30) not null unique,
        material_code varchar(30),
        ref_doc_no varchar(30),
        ref_doc_type varchar(30),
        created_by varchar(50),
        inspector varchar(50),
        material_name varchar(100),
        remark varchar(500),
        result_remark varchar(500),
        primary key (id)
    );

    create table quality_inspection_item (
        sort_order integer,
        create_time timestamp,
        id integer,
        inspection_id bigint not null,
        template_id bigint,
        update_time timestamp,
        item_result varchar(20),
        unit varchar(30),
        name varchar(100) not null,
        measured_value varchar(200),
        method varchar(200),
        standard varchar(200),
        primary key (id)
    );

    create table quotation (
        quote_date date,
        total_amount numeric(14,2),
        valid_until date,
        create_time timestamp,
        customer_id bigint not null,
        id integer,
        update_time timestamp,
        quote_no varchar(20) not null unique,
        sales_order_no varchar(20),
        status varchar(20) not null,
        created_by varchar(50),
        customer_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table quotation_item (
        amount numeric(14,2),
        qty numeric(14,3) not null,
        unit_price numeric(12,2),
        id integer,
        quotation_id bigint not null,
        unit varchar(10),
        material_code varchar(30) not null,
        material_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table raw_material_purchase (
        increase_amount numeric(12,2),
        increase_rate numeric(8,2),
        is_free boolean,
        last_unit_price numeric(12,2),
        purchase_date date,
        qty numeric(14,3),
        received_qty numeric(14,3),
        tax_rate numeric(5,2),
        total_amount numeric(14,2),
        unit_price numeric(12,2),
        create_time timestamp,
        id integer,
        supplier_id bigint,
        update_time timestamp,
        status varchar(20),
        category varchar(30),
        material_code varchar(30),
        order_no varchar(30) not null,
        sub_category varchar(30),
        warehouse_id varchar(30),
        brand varchar(50),
        created_by varchar(50),
        supplier_name varchar(50),
        manufacturer varchar(100),
        material_name varchar(100),
        file_path varchar(200),
        remark varchar(500),
        primary key (id)
    );

    create table rd_progress (
        closed_date date,
        next_date date,
        raise_date date,
        create_time timestamp,
        id integer,
        update_time timestamp,
        category varchar(20) not null,
        created_by varchar(50),
        owner varchar(50) not null,
        progress varchar(500),
        content varchar(2000) not null,
        result varchar(2000),
        primary key (id)
    );

    create table recipe (
        enabled boolean,
        print_count integer not null,
        create_time timestamp,
        id integer,
        packaging_standard_id bigint,
        process_template_id bigint,
        qc_template_id bigint,
        update_time timestamp,
        recipe_type varchar(20) not null,
        product_code varchar(30),
        recipe_no varchar(30) not null unique,
        category varchar(50),
        product_name varchar(100) not null,
        description varchar(500),
        primary key (id)
    );

    create table recipe_change_log (
        create_time timestamp,
        id integer,
        recipe_id bigint,
        version_id bigint,
        version_no varchar(10),
        action varchar(20),
        recipe_no varchar(30),
        operator varchar(50),
        product_name varchar(100),
        detail varchar(1000),
        primary key (id)
    );

    create table recipe_tree_node (
        qty numeric(14,3) not null,
        sort_order integer,
        id integer,
        parent_node_id bigint,
        ref_recipe_id bigint,
        version_id bigint not null,
        unit varchar(10),
        node_type varchar(20) not null,
        material_code varchar(30),
        category varchar(50),
        sub_category varchar(50),
        material_name varchar(100),
        spec varchar(100),
        remark varchar(200),
        primary key (id)
    );

    create table recipe_version (
        batch_qty numeric(14,3),
        effective_date date,
        create_time timestamp,
        id integer,
        recipe_id bigint not null,
        released_time timestamp,
        update_time timestamp,
        unit varchar(10),
        status varchar(20) not null,
        version_no varchar(20) not null,
        created_by varchar(50),
        released_by varchar(50),
        remark varchar(500),
        primary key (id)
    );

    create table return_order (
        qty numeric(14,3) not null,
        return_amount numeric(14,2),
        unit_price numeric(14,2),
        approve_time timestamp,
        create_time timestamp,
        customer_id bigint,
        id integer,
        ref_arrival_id bigint,
        supplier_id bigint,
        update_time timestamp,
        unit varchar(10),
        doc_no varchar(20) not null unique,
        inbound_doc_no varchar(20),
        location_id varchar(20),
        outbound_doc_no varchar(20),
        settle_type varchar(20),
        status varchar(20) not null,
        type varchar(20) not null,
        warehouse_id varchar(20),
        material_code varchar(30) not null,
        purchase_order_no varchar(30),
        qc_inspection_no varchar(30),
        ref_sales_outbound_no varchar(30),
        sales_order_no varchar(30),
        approved_by varchar(50),
        batch_no varchar(50),
        created_by varchar(50),
        location_name varchar(50),
        customer_name varchar(100),
        material_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table salary_item (
        base numeric(12,2),
        bonus numeric(12,2),
        deduction numeric(12,2),
        gross numeric(12,2) not null,
        income_tax numeric(12,2),
        net numeric(12,2) not null,
        piecework numeric(12,2),
        social_ins numeric(12,2),
        employee_id bigint not null,
        id integer,
        sheet_id bigint not null,
        dept varchar(20) not null,
        employee_name varchar(50) not null,
        primary key (id)
    );

    create table salary_sheet (
        total_gross numeric(14,2) not null,
        total_net numeric(14,2) not null,
        confirmed_time timestamp,
        create_time timestamp,
        id integer,
        update_time timestamp,
        period varchar(10) not null,
        doc_no varchar(20) not null unique,
        status varchar(20) not null,
        confirmed_by varchar(50),
        created_by varchar(50),
        remark varchar(500),
        primary key (id)
    );

    create table sales_order (
        credit_exceeded boolean,
        expected_ship_date date,
        order_date date,
        tax_rate numeric(5,2),
        total_amount numeric(14,2),
        create_time timestamp,
        customer_id bigint not null,
        id integer,
        update_time timestamp,
        contract_no varchar(20),
        order_no varchar(20) not null unique,
        source_warehouse_id varchar(20) not null,
        status varchar(20) not null,
        created_by varchar(50),
        customer_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table sales_order_change_log (
        create_time timestamp,
        id integer,
        order_id bigint not null,
        order_no varchar(20),
        operator varchar(50),
        detail varchar(2000) not null,
        primary key (id)
    );

    create table sales_order_item (
        amount numeric(14,2),
        qty numeric(14,3) not null,
        return_qty numeric(14,3),
        shipped_qty numeric(14,3),
        unit_price numeric(12,2),
        id integer,
        order_id bigint not null,
        unit varchar(10),
        material_code varchar(30) not null,
        material_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table sales_outbound (
        cost numeric(14,2),
        print_count integer not null,
        qty numeric(14,3) not null,
        unit_price numeric(14,2),
        create_time timestamp,
        id integer,
        update_time timestamp,
        unit varchar(10),
        location_id varchar(20),
        status varchar(20) not null,
        warehouse_id varchar(20) not null,
        batch_no varchar(30),
        doc_no varchar(30) not null unique,
        material_code varchar(30) not null,
        sales_order_no varchar(30),
        created_by varchar(50),
        customer_name varchar(100),
        material_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table sample_formula (
        est_cost numeric(14,2),
        total_qty numeric(14,3),
        converted_recipe_id bigint,
        converted_time timestamp,
        create_time timestamp,
        id integer,
        sample_request_id bigint not null unique,
        update_time timestamp,
        color_series varchar(10),
        main_material varchar(10),
        sub_category varchar(10),
        formula_no varchar(20) not null unique,
        sample_location varchar(20),
        material_code varchar(30),
        material_name varchar(100),
        primary key (id)
    );

    create table sample_formula_history (
        round integer,
        create_time timestamp,
        formula_id bigint not null,
        id integer,
        snapshot TEXT,
        primary key (id)
    );

    create table sample_formula_item (
        qty numeric(14,3) not null,
        sort_order integer,
        formula_id bigint not null,
        id integer,
        category varchar(10),
        sub_category varchar(10),
        unit varchar(10),
        material_code varchar(30) not null,
        material_name varchar(100),
        primary key (id)
    );

    create table sample_request (
        adjust_count integer,
        apply_date date,
        feedback_date date,
        qty numeric(14,3),
        send_date date,
        assign_time timestamp,
        create_time timestamp,
        customer_id bigint,
        id integer,
        rd_progress_id bigint,
        receive_time timestamp,
        ref_sample_id bigint,
        update_time timestamp,
        sample_size varchar(10),
        unit varchar(10),
        sample_no varchar(20) not null unique,
        status varchar(20) not null,
        won_order_no varchar(20),
        material_code varchar(30),
        applicant varchar(50),
        assignee varchar(50),
        colorist varchar(50),
        express_no varchar(50),
        customer_name varchar(100) not null,
        loss_reason varchar(200),
        color_note varchar(500),
        material_desc varchar(500) not null,
        remark varchar(500),
        feedback_content varchar(1000),
        primary key (id)
    );

    create table shipping_log (
        freight numeric(12,2) not null,
        create_time timestamp,
        id integer,
        update_time timestamp,
        ship_date varchar(10),
        borne varchar(20) not null,
        doc_no varchar(20) not null unique,
        outbound_doc_no varchar(30),
        sales_order_no varchar(30) not null,
        carrier varchar(50),
        created_by varchar(50),
        customer_name varchar(50),
        tracking_no varchar(50),
        remark varchar(500),
        primary key (id)
    );

    create table stat_finance_summary (
        ap_paid numeric(16,2) not null,
        ap_total numeric(16,2) not null,
        ar_received numeric(16,2) not null,
        ar_total numeric(16,2) not null,
        id bigint not null,
        update_time timestamp,
        primary key (id)
    );

    create table stat_inventory_daily (
        in_amount numeric(16,2) not null,
        in_qty numeric(14,3) not null,
        out_qty numeric(14,3) not null,
        id integer,
        stat_date varchar(10) not null,
        warehouse_id varchar(20) not null,
        material_code varchar(30) not null,
        primary key (id)
    );

    create table stat_material_usage (
        out_qty numeric(14,3) not null,
        usage_days integer not null,
        period varchar(7) not null,
        id integer,
        warehouse_id varchar(20),
        material_code varchar(30) not null,
        primary key (id)
    );

    create table stat_order_monthly (
        order_count integer not null,
        total_amount numeric(16,2) not null,
        period varchar(7) not null,
        id integer,
        order_type varchar(20) not null,
        primary key (id)
    );

    create table stock_check (
        actual_qty numeric(14,3),
        adjust_qty numeric(14,3),
        diff_qty numeric(14,3),
        system_qty numeric(14,3),
        create_time timestamp,
        id integer,
        doc_type varchar(20) not null,
        from_location_id varchar(20),
        status varchar(20) not null,
        to_location_id varchar(20),
        warehouse_id varchar(20) not null,
        batch_no varchar(30),
        doc_no varchar(30) not null unique,
        material_code varchar(30) not null,
        from_location_name varchar(50),
        operator varchar(50),
        to_location_name varchar(50),
        material_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table supplier (
        blacklisted boolean,
        enabled boolean,
        processing_fee numeric(38,2),
        create_time timestamp,
        id integer,
        update_time timestamp,
        code varchar(20) not null unique,
        payment_method varchar(20),
        type varchar(20),
        name varchar(100) not null,
        payment_terms varchar(500),
        primary key (id)
    );

    create table supplier_quality_trace (
        arrival_date date,
        compensation_amount numeric(14,2),
        issue_date date,
        loss_amount numeric(38,2),
        print_count integer not null,
        purchase_amount numeric(38,2),
        purchase_qty numeric(38,2),
        purchase_unit_price numeric(38,2),
        resolve_date date,
        create_time timestamp,
        id integer,
        supplier_id bigint,
        update_time timestamp,
        category varchar(20),
        order_category varchar(20),
        qc_inspection_no varchar(20),
        qc_status varchar(20),
        status varchar(20) not null,
        trace_no varchar(20) not null unique,
        batch_no varchar(30) not null,
        material_code varchar(30),
        purchase_order_no varchar(30),
        result_type varchar(30),
        created_by varchar(50),
        handler varchar(50),
        material_name varchar(100),
        supplier_name varchar(100) not null,
        remark varchar(500),
        result_remark varchar(1000),
        description varchar(2000) not null,
        primary key (id)
    );

    create table sys_role (
        enabled boolean,
        create_time timestamp,
        id integer,
        update_time timestamp,
        code varchar(30) not null unique,
        name varchar(50) not null,
        primary key (id)
    );

    create table sys_role_permission (
        id integer,
        perm_code varchar(30) not null,
        role_code varchar(30) not null,
        primary key (id)
    );

    create table sys_user (
        enabled boolean,
        must_change_pwd boolean,
        create_time timestamp,
        id integer,
        update_time timestamp,
        phone varchar(20),
        role varchar(30) not null,
        real_name varchar(50) not null,
        username varchar(50) not null unique,
        password varchar(100) not null,
        primary key (id)
    );

    create table task (
        due_date date,
        completed_at timestamp,
        create_time timestamp,
        id integer,
        update_time timestamp,
        priority varchar(10) not null,
        status varchar(20) not null,
        doc_no varchar(25) not null unique,
        created_by varchar(50),
        owner varchar(50) not null,
        title varchar(200) not null,
        collaborators varchar(500),
        remark varchar(500),
        completed_note varchar(1000),
        description varchar(2000),
        primary key (id)
    );

    create table task_progress (
        create_time timestamp,
        id integer,
        task_id bigint not null,
        action_type varchar(20) not null,
        reporter varchar(50) not null,
        content varchar(1000),
        primary key (id)
    );

    create table voucher (
        attachment_count integer,
        total_credit numeric(14,2) not null,
        total_debit numeric(14,2) not null,
        voucher_date date not null,
        create_time timestamp,
        id integer,
        posted_time timestamp,
        update_time timestamp,
        period varchar(10) not null,
        doc_no varchar(20) not null unique,
        source varchar(20) not null,
        status varchar(20) not null,
        ref_doc_no varchar(30),
        created_by varchar(50),
        posted_by varchar(50),
        remark varchar(500),
        primary key (id)
    );

    create table voucher_entry (
        credit numeric(14,2),
        debit numeric(14,2),
        line_no integer,
        id integer,
        voucher_id bigint not null,
        aux_type varchar(20),
        subject_code varchar(20) not null,
        aux_name varchar(100),
        subject_name varchar(100) not null,
        digest varchar(200),
        primary key (id)
    );

    create table warehouse (
        enabled boolean,
        create_time timestamp,
        id integer,
        update_time timestamp,
        code varchar(20) not null unique,
        contact_phone varchar(20),
        processor_id varchar(20),
        warehouse_type varchar(20) not null,
        contact_person varchar(50),
        name varchar(50) not null,
        processor_name varchar(100),
        address varchar(200),
        remark varchar(500),
        primary key (id)
    );

    create table warehouse_location (
        enabled boolean,
        sort_order integer,
        create_time timestamp,
        id integer,
        update_time timestamp,
        zone_id bigint not null,
        code varchar(20) not null,
        name varchar(50) not null,
        remark varchar(500),
        primary key (id)
    );

    create table warehouse_zone (
        enabled boolean,
        sort_order integer,
        create_time timestamp,
        id integer,
        update_time timestamp,
        warehouse_id bigint not null,
        code varchar(20) not null,
        zone_type varchar(20),
        name varchar(50) not null,
        remark varchar(500),
        primary key (id)
    );

    create table weekly_topic (
        closed_date date,
        plan_date date,
        create_time timestamp,
        id integer,
        update_time timestamp,
        category varchar(20) not null,
        created_by varchar(50),
        owner varchar(50) not null,
        content varchar(2000) not null,
        result varchar(2000),
        primary key (id)
    );

    create table account_mapping (
        id integer,
        update_time timestamp,
        subject_code varchar(20) not null,
        map_key varchar(60) not null unique,
        remark varchar(200),
        primary key (id)
    );

    create table account_period (
        closed boolean not null,
        close_time timestamp,
        id integer,
        period varchar(10) not null unique,
        closed_by varchar(50),
        primary key (id)
    );

    create table account_subject (
        opening_balance numeric(14,2),
        direction varchar(5) not null,
        opening_direction varchar(5),
        create_time timestamp,
        id integer,
        update_time timestamp,
        category varchar(20) not null,
        code varchar(20) not null unique,
        parent_code varchar(20),
        status varchar(20) not null,
        name varchar(100) not null,
        primary key (id)
    );

    create table accounts_payable (
        amount numeric(14,2) not null,
        due_date date,
        paid_amount numeric(14,2),
        arrival_id bigint,
        create_time timestamp,
        id integer,
        supplier_id bigint not null,
        update_time timestamp,
        doc_no varchar(20) not null unique,
        outsource_order_no varchar(20),
        payable_type varchar(20) not null,
        purchase_order_no varchar(20),
        status varchar(20) not null,
        remark varchar(500),
        primary key (id)
    );

    create table accounts_receivable (
        amount numeric(14,2) not null,
        due_date date,
        received_amount numeric(14,2),
        create_time timestamp,
        customer_id bigint not null,
        id integer,
        update_time timestamp,
        contract_no varchar(20),
        doc_no varchar(20) not null unique,
        sales_order_no varchar(20),
        status varchar(20) not null,
        remark varchar(500),
        primary key (id)
    );

    create table advance_payment (
        amount numeric(14,2) not null,
        pay_date date,
        used_amount numeric(14,2) not null,
        create_time timestamp,
        id integer,
        partner_id bigint,
        update_time timestamp,
        direction varchar(20) not null,
        doc_no varchar(20) not null unique,
        method varchar(20),
        status varchar(20) not null,
        created_by varchar(50),
        partner_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table asset (
        original_value numeric(14,2) not null,
        purchase_date date,
        residual_rate numeric(5,2),
        scrap_date date,
        useful_life_months integer not null,
        create_time timestamp,
        id integer,
        update_time timestamp,
        category varchar(20) not null,
        doc_no varchar(20) not null unique,
        expense_subject varchar(20) not null,
        status varchar(20) not null,
        keeper varchar(50),
        location varchar(100),
        name varchar(100) not null,
        remark varchar(500),
        primary key (id)
    );

    create table asset_depreciation (
        amount numeric(14,2) not null,
        asset_id bigint not null,
        create_time timestamp,
        id integer,
        voucher_id bigint,
        period varchar(10) not null,
        expense_subject varchar(20) not null,
        asset_name varchar(100) not null,
        primary key (id)
    );

    create table bank_account (
        opening_balance numeric(16,2),
        create_time timestamp,
        id integer,
        enabled varchar(10),
        account_no varchar(30),
        name varchar(50) not null,
        bank_name varchar(100),
        primary key (id)
    );

    create table bank_statement (
        amount numeric(16,2) not null,
        balance numeric(16,2),
        tx_date date not null,
        account_id bigint not null,
        create_time timestamp,
        id integer,
        ref_id bigint,
        status varchar(10),
        ref_type varchar(20),
        import_batch varchar(30),
        counterparty varchar(100),
        summary varchar(200),
        primary key (id)
    );

    create table coding_rule (
        current_seq integer not null,
        enabled boolean,
        number_start integer not null,
        create_time timestamp,
        id integer,
        update_time timestamp,
        category_code varchar(10) not null,
        sub_category_code varchar(10) not null,
        category varchar(50) not null,
        sub_category varchar(50) not null,
        primary key (id)
    );

    create table crm_contact (
        is_primary boolean,
        create_time timestamp,
        customer_id bigint,
        id integer,
        update_time timestamp,
        phone varchar(30),
        name varchar(50) not null,
        title varchar(50),
        wechat varchar(50),
        company_name varchar(100) not null,
        email varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table crm_follow_up (
        follow_date date,
        next_date date,
        create_time timestamp,
        customer_id bigint,
        id integer,
        opportunity_id bigint,
        method varchar(20) not null,
        operator varchar(50),
        content varchar(2000) not null,
        primary key (id)
    );

    create table crm_opportunity (
        expect_amount numeric(14,2),
        expect_date date,
        create_time timestamp,
        customer_id bigint,
        id integer,
        update_time timestamp,
        stage varchar(20) not null,
        won_order_no varchar(20),
        created_by varchar(50),
        owner varchar(50),
        company_name varchar(100) not null,
        title varchar(100) not null,
        loss_reason varchar(200),
        product_interest varchar(200),
        remark varchar(500),
        primary key (id)
    );

    create table customer (
        blacklisted boolean,
        credit_limit numeric(14,2),
        enabled boolean,
        create_time timestamp,
        id integer,
        update_time timestamp,
        abc_level varchar(10),
        code varchar(20) not null unique,
        contact_phone varchar(20),
        payment_method varchar(20),
        tax_no varchar(30),
        bank_account varchar(50),
        contact_person varchar(50),
        legal_person varchar(50),
        bank_name varchar(100),
        name varchar(100) not null,
        address varchar(200),
        payment_terms varchar(500),
        remark varchar(500),
        primary key (id)
    );

    create table customer_complaint (
        close_date date,
        complaint_date date,
        resolve_date date,
        create_time timestamp,
        customer_id bigint,
        id integer,
        update_time timestamp,
        category varchar(20),
        complaint_no varchar(20) not null unique,
        qc_doc_no varchar(20),
        sales_order_no varchar(20),
        status varchar(20) not null,
        batch_no varchar(30),
        material_code varchar(30),
        created_by varchar(50),
        handler varchar(50),
        customer_name varchar(100) not null,
        material_name varchar(100),
        remark varchar(500),
        action varchar(1000),
        cause varchar(1000),
        description varchar(2000) not null,
        primary key (id)
    );

    create table dict_item (
        enabled boolean,
        sort_order integer,
        create_time timestamp,
        id integer,
        type varchar(50) not null,
        value varchar(100) not null,
        label varchar(200),
        primary key (id)
    );

    create table employee (
        base_salary numeric(12,2),
        hire_date date,
        leave_date date,
        create_time timestamp,
        id integer,
        update_time timestamp,
        dept varchar(20) not null,
        status varchar(20) not null,
        phone varchar(30),
        bank_card varchar(50),
        name varchar(50) not null,
        position varchar(50),
        remark varchar(500),
        primary key (id)
    );

    create table expense (
        amount numeric(14,2) not null,
        occur_date date,
        create_time timestamp,
        id integer,
        update_time timestamp,
        direction varchar(20) not null,
        doc_no varchar(20) not null unique,
        method varchar(20),
        created_by varchar(50),
        expense_type varchar(50) not null,
        handler varchar(50),
        partner varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table finished_product_purchase (
        is_free boolean,
        purchase_date date,
        qty numeric(14,3),
        received_qty numeric(14,3),
        tax_rate numeric(5,2),
        total_amount numeric(14,2),
        unit_price numeric(12,2),
        create_time timestamp,
        id integer,
        supplier_id bigint,
        update_time timestamp,
        status varchar(20),
        material_code varchar(30),
        order_no varchar(30) not null unique,
        warehouse_id varchar(30),
        brand varchar(50),
        created_by varchar(50),
        material_name varchar(100),
        supplier_name varchar(100),
        file_path varchar(200),
        remark varchar(500),
        primary key (id)
    );

    create table inventory_ledger (
        amount numeric(14,2),
        available_qty numeric(14,3),
        expiry_date date,
        in_transit_qty numeric(14,3),
        inbound_date date,
        occupied_qty numeric(14,3),
        produce_date date,
        qc_date date,
        qty numeric(14,3),
        unit_price numeric(14,2),
        create_time timestamp,
        id integer,
        last_update_time timestamp,
        unit varchar(10),
        location_id varchar(20),
        ownership_type varchar(20) not null,
        qc_status varchar(20),
        warehouse_id varchar(20) not null,
        batch_no varchar(30),
        material_code varchar(30) not null,
        qc_inspection_no varchar(30),
        location_name varchar(50),
        qc_inspector varchar(50),
        zone_name varchar(50),
        material_name varchar(100),
        qc_result varchar(500),
        primary key (id)
    );

    create table inventory_movement (
        qty numeric(14,3) not null,
        qty_after numeric(14,3),
        qty_before numeric(14,3),
        direction varchar(5) not null,
        create_time timestamp,
        id integer,
        doc_type varchar(20) not null,
        location_id varchar(20),
        ownership_type varchar(20) not null,
        warehouse_id varchar(20) not null,
        batch_no varchar(30),
        doc_no varchar(30) not null,
        material_code varchar(30) not null,
        operator varchar(50),
        remark varchar(500),
        primary key (id)
    );

    create table invoice (
        amount numeric(14,2) not null,
        invoice_date date,
        tax_amount numeric(14,2),
        tax_rate integer,
        total_amount numeric(14,2),
        create_time timestamp,
        id integer,
        partner_id bigint,
        update_time timestamp,
        direction varchar(20) not null,
        doc_no varchar(20) not null unique,
        flush_doc_no varchar(20),
        partner_type varchar(20) not null,
        status varchar(20) not null,
        invoice_no varchar(30),
        partner_tax_no varchar(30),
        ref_order_no varchar(30),
        created_by varchar(50),
        partner_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table loss_letter_template (
        enabled boolean not null,
        is_default boolean not null,
        create_time timestamp,
        id integer,
        update_time timestamp,
        name varchar(50) not null,
        closing_text varchar(1000),
        opening_text varchar(1000),
        require_text varchar(1000),
        body_text varchar(2000) not null,
        primary key (id)
    );

    create table material (
        enabled boolean,
        shelf_life_days integer,
        create_time timestamp,
        id integer,
        update_time timestamp,
        color_series varchar(20),
        main_material varchar(20),
        code varchar(30) not null unique,
        brand varchar(50),
        category varchar(50),
        sub_category varchar(50),
        brand_owner varchar(100),
        name varchar(100) not null,
        alternative_codes varchar(500),
        primary key (id)
    );

    create table other_inbound (
        finance_amount numeric(14,2),
        gen_finance boolean,
        price numeric(12,4),
        qty numeric(14,3) not null,
        return_amount numeric(14,2),
        tax_rate numeric(5,2),
        create_time timestamp,
        customer_id bigint,
        finance_partner_id bigint,
        id integer,
        return_ref_id bigint,
        supplier_id bigint,
        update_time timestamp,
        unit varchar(10),
        doc_no varchar(20) not null unique,
        finance_doc_no varchar(20),
        location_id varchar(20),
        reason varchar(20),
        return_offset_status varchar(20),
        return_ref_type varchar(20),
        status varchar(20) not null,
        warehouse_id varchar(20) not null,
        batch_no varchar(30),
        material_code varchar(30) not null,
        return_ref_doc_no varchar(30),
        created_by varchar(50),
        finance_partner_name varchar(50),
        material_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table other_outbound (
        cost numeric(14,2),
        finance_amount numeric(14,2),
        gen_finance boolean,
        qty numeric(14,3) not null,
        unit_price numeric(14,2),
        create_time timestamp,
        finance_partner_id bigint,
        id integer,
        return_order_id bigint,
        update_time timestamp,
        unit varchar(10),
        doc_no varchar(20) not null unique,
        finance_doc_no varchar(20),
        location_id varchar(20),
        reason varchar(20),
        return_ref_doc_no varchar(20),
        status varchar(20) not null,
        warehouse_id varchar(20) not null,
        batch_no varchar(30),
        material_code varchar(30) not null,
        created_by varchar(50),
        finance_partner_name varchar(50),
        material_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table outsource_finish_inbound (
        qty numeric(14,3) not null,
        theoretical_qty numeric(14,3),
        yield_rate numeric(8,2),
        create_time timestamp,
        id integer,
        update_time timestamp,
        unit varchar(10),
        doc_no varchar(20) not null unique,
        location_id varchar(20),
        outsource_order_no varchar(20) not null,
        status varchar(20) not null,
        warehouse_id varchar(20) not null,
        batch_no varchar(30),
        created_by varchar(50),
        location_name varchar(50),
        product_code varchar(50),
        zone_name varchar(50),
        product_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table outsource_material_consume (
        consume_qty numeric(14,3) not null,
        remain_qty numeric(14,3),
        id integer,
        unit varchar(10),
        inbound_doc_no varchar(20) not null,
        outsource_order_no varchar(20) not null,
        batch_no varchar(30),
        material_code varchar(30) not null,
        remark varchar(200),
        primary key (id)
    );

    create table outsource_material_outbound (
        cost numeric(14,2),
        qty numeric(14,3) not null,
        signed_diff numeric(14,3),
        signed_qty numeric(14,3),
        unit_price numeric(14,2),
        create_time timestamp,
        id integer,
        update_time timestamp,
        unit varchar(10),
        doc_no varchar(20) not null unique,
        from_warehouse_id varchar(20) not null,
        location_id varchar(20),
        outsource_order_no varchar(20) not null,
        processor_id varchar(20) not null,
        status varchar(20) not null,
        to_warehouse_id varchar(20),
        batch_no varchar(30),
        material_code varchar(30) not null,
        created_by varchar(50),
        location_name varchar(50),
        zone_name varchar(50),
        processor_name varchar(100),
        signed_diff_reason varchar(200),
        remark varchar(500),
        primary key (id)
    );

    create table outsource_order (
        batch_qty numeric(14,3) not null,
        print_count integer not null,
        processing_fee numeric(12,2),
        schedule_seq integer,
        create_time timestamp,
        id integer,
        recipe_version_id bigint,
        supplier_id bigint,
        update_time timestamp,
        unit varchar(10),
        order_no varchar(20) not null unique,
        sales_order_no varchar(20),
        status varchar(20) not null,
        created_by varchar(50),
        product_code varchar(50),
        processor varchar(100),
        product_name varchar(100) not null,
        remark varchar(500),
        primary key (id)
    );

    create table outsource_order_item (
        qty numeric(14,3) not null,
        id integer,
        order_id bigint not null,
        ref_recipe_id bigint,
        unit varchar(10),
        node_type varchar(20),
        material_code varchar(30) not null,
        material_name varchar(100),
        spec varchar(100),
        remark varchar(200),
        primary key (id)
    );

    create table packaging_standard (
        capacity_kg numeric(10,3),
        enabled boolean,
        unit_price numeric(14,2) not null,
        create_time timestamp,
        id integer,
        update_time timestamp,
        pack_type varchar(20) not null,
        name varchar(100) not null,
        spec varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table packaging_standard_item (
        qty numeric(10,3) not null,
        sort_order integer,
        unit_price numeric(14,2) not null,
        id integer,
        packaging_id bigint not null,
        pack_type varchar(20),
        name varchar(100) not null,
        spec varchar(100),
        primary key (id)
    );

    create table payment_disbursement (
        amount numeric(14,2) not null,
        pay_date date not null,
        ap_id bigint,
        create_time timestamp,
        id integer,
        supplier_id bigint not null,
        ap_doc_no varchar(20),
        doc_no varchar(20) not null unique,
        method varchar(20) not null,
        bank_account varchar(50),
        operator varchar(50),
        supplier_name varchar(50),
        remark varchar(500),
        primary key (id)
    );

    create table payment_receipt (
        amount numeric(14,2) not null,
        receipt_date date not null,
        ar_id bigint,
        create_time timestamp,
        customer_id bigint not null,
        id integer,
        ar_doc_no varchar(20),
        doc_no varchar(20) not null unique,
        method varchar(20) not null,
        bank_account varchar(50),
        customer_name varchar(50),
        operator varchar(50),
        remark varchar(500),
        primary key (id)
    );

    create table price_policy (
        effective_date date not null,
        expiry_date date,
        min_qty numeric(14,3),
        unit_price numeric(14,2) not null,
        material_category varchar(5),
        create_time timestamp,
        id integer,
        status varchar(10),
        material_code varchar(30),
        created_by varchar(50),
        remark varchar(200),
        primary key (id)
    );

    create table process_qc_item (
        sort_order integer,
        test_times integer,
        id integer,
        stage_id bigint not null,
        unit varchar(20),
        name varchar(50) not null,
        standard varchar(100),
        method varchar(200),
        primary key (id)
    );

    create table process_stage (
        sort_order integer,
        id integer,
        template_id bigint not null,
        stage_no varchar(10),
        role_hint varchar(50),
        stage_name varchar(50) not null,
        primary key (id)
    );

    create table process_step (
        sort_order integer,
        id integer,
        stage_id bigint not null,
        step_code varchar(10),
        params varchar(200),
        description varchar(1000),
        primary key (id)
    );

    create table process_template (
        enabled boolean,
        is_default boolean not null,
        create_time timestamp,
        id integer,
        update_time timestamp,
        recipe_type varchar(20) not null,
        created_by varchar(50),
        name varchar(50) not null,
        packing_requirement varchar(1000),
        primary key (id)
    );

    create table production_inbound (
        qty numeric(14,3) not null,
        theoretical_qty numeric(14,3),
        yield_rate numeric(8,2),
        create_time timestamp,
        id integer,
        update_time timestamp,
        unit varchar(10),
        doc_no varchar(20) not null unique,
        location_id varchar(20),
        production_order_no varchar(20),
        status varchar(20) not null,
        warehouse_id varchar(20) not null,
        batch_no varchar(30),
        created_by varchar(50),
        location_name varchar(50),
        product_code varchar(50),
        zone_name varchar(50),
        product_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table production_order (
        batch_qty numeric(14,3) not null,
        print_count integer not null,
        schedule_seq integer,
        create_time timestamp,
        id integer,
        recipe_version_id bigint,
        update_time timestamp,
        unit varchar(10),
        order_no varchar(20) not null unique,
        sales_order_no varchar(20),
        status varchar(20) not null,
        created_by varchar(50),
        product_code varchar(50),
        product_name varchar(100) not null,
        remark varchar(500),
        primary key (id)
    );

    create table production_order_exception (
        input_qty numeric(14,3),
        io_ratio numeric(8,2),
        output_qty numeric(14,3),
        closed_time timestamp,
        create_time timestamp,
        id integer,
        update_time timestamp,
        order_no varchar(20) not null unique,
        status varchar(20) not null,
        created_by varchar(50),
        handler varchar(50),
        reason varchar(200),
        measure varchar(500),
        remark varchar(500),
        primary key (id)
    );

    create table production_order_item (
        qty numeric(14,3) not null,
        id integer,
        order_id bigint not null,
        ref_recipe_id bigint,
        unit varchar(10),
        node_type varchar(20),
        material_code varchar(30) not null,
        material_name varchar(100),
        spec varchar(100),
        remark varchar(200),
        primary key (id)
    );

    create table production_outbound (
        cost numeric(14,2),
        qty numeric(14,3) not null,
        unit_price numeric(14,2),
        create_time timestamp,
        id integer,
        update_time timestamp,
        unit varchar(10),
        doc_no varchar(20) not null unique,
        doc_type varchar(20) not null,
        location_id varchar(20),
        production_order_no varchar(20),
        status varchar(20) not null,
        supplement_type varchar(20),
        warehouse_id varchar(20) not null,
        batch_no varchar(30),
        material_code varchar(30) not null,
        created_by varchar(50),
        location_name varchar(50),
        zone_name varchar(50),
        material_name varchar(100),
        product_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table purchase_arrival (
        arrival_date date,
        qty numeric(14,3),
        tax_rate numeric(5,2),
        unit_price numeric(14,4),
        create_time timestamp,
        id integer,
        supplier_id bigint,
        type varchar(10) not null,
        unit varchar(10),
        location_id varchar(20),
        status varchar(20) not null,
        warehouse_id varchar(20),
        batch_no varchar(30),
        doc_no varchar(30),
        material_code varchar(30),
        ref_order_no varchar(30) not null,
        location_name varchar(50),
        zone_name varchar(50),
        material_name varchar(100),
        operator varchar(100),
        supplier_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table purchase_order (
        expected_delivery_date date,
        order_date date,
        total_amount numeric(14,2),
        create_time timestamp,
        id integer,
        supplier_id bigint,
        update_time timestamp,
        order_no varchar(20) not null unique,
        status varchar(20) not null,
        target_warehouse_id varchar(20),
        created_by varchar(50),
        payment_terms varchar(200),
        remark varchar(500),
        primary key (id)
    );

    create table purchase_order_item (
        amount numeric(14,2),
        qty numeric(14,3) not null,
        received_qty numeric(14,3),
        return_qty numeric(14,3),
        unit_price numeric(12,2),
        id integer,
        order_id bigint not null,
        unit varchar(10),
        material_code varchar(30) not null,
        remark varchar(500),
        primary key (id)
    );

    create table qc_template (
        enabled boolean,
        is_default boolean,
        create_time timestamp,
        id integer,
        update_time timestamp,
        apply_category varchar(10) not null,
        color_series varchar(20),
        main_material varchar(20),
        created_by varchar(50),
        sub_category varchar(50),
        name varchar(100) not null,
        remark varchar(500),
        primary key (id)
    );

    create table qc_template_item (
        sort_order integer,
        id integer,
        template_id bigint not null,
        unit varchar(30),
        name varchar(100) not null,
        method varchar(200),
        standard varchar(200),
        primary key (id)
    );

    create table quality_inspection (
        inspect_date date,
        print_count integer not null,
        produce_date date,
        qty numeric(14,3),
        unit_price numeric(14,2),
        material_category varchar(5),
        arrival_id bigint,
        create_time timestamp,
        id integer,
        update_time timestamp,
        unit varchar(10),
        location_id varchar(20),
        status varchar(20) not null,
        type varchar(20) not null,
        warehouse_id varchar(20),
        batch_no varchar(30),
        inspection_no varchar(30) not null unique,
        material_code varchar(30),
        ref_doc_no varchar(30),
        ref_doc_type varchar(30),
        created_by varchar(50),
        inspector varchar(50),
        material_name varchar(100),
        remark varchar(500),
        result_remark varchar(500),
        primary key (id)
    );

    create table quality_inspection_item (
        sort_order integer,
        create_time timestamp,
        id integer,
        inspection_id bigint not null,
        template_id bigint,
        update_time timestamp,
        item_result varchar(20),
        unit varchar(30),
        name varchar(100) not null,
        measured_value varchar(200),
        method varchar(200),
        standard varchar(200),
        primary key (id)
    );

    create table quotation (
        quote_date date,
        total_amount numeric(14,2),
        valid_until date,
        create_time timestamp,
        customer_id bigint not null,
        id integer,
        update_time timestamp,
        quote_no varchar(20) not null unique,
        sales_order_no varchar(20),
        status varchar(20) not null,
        created_by varchar(50),
        customer_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table quotation_item (
        amount numeric(14,2),
        qty numeric(14,3) not null,
        unit_price numeric(12,2),
        id integer,
        quotation_id bigint not null,
        unit varchar(10),
        material_code varchar(30) not null,
        material_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table raw_material_purchase (
        increase_amount numeric(12,2),
        increase_rate numeric(8,2),
        is_free boolean,
        last_unit_price numeric(12,2),
        purchase_date date,
        qty numeric(14,3),
        received_qty numeric(14,3),
        tax_rate numeric(5,2),
        total_amount numeric(14,2),
        unit_price numeric(12,2),
        create_time timestamp,
        id integer,
        supplier_id bigint,
        update_time timestamp,
        status varchar(20),
        category varchar(30),
        material_code varchar(30),
        order_no varchar(30) not null,
        sub_category varchar(30),
        warehouse_id varchar(30),
        brand varchar(50),
        created_by varchar(50),
        supplier_name varchar(50),
        manufacturer varchar(100),
        material_name varchar(100),
        file_path varchar(200),
        remark varchar(500),
        primary key (id)
    );

    create table rd_progress (
        closed_date date,
        next_date date,
        raise_date date,
        create_time timestamp,
        id integer,
        update_time timestamp,
        category varchar(20) not null,
        created_by varchar(50),
        owner varchar(50) not null,
        progress varchar(500),
        content varchar(2000) not null,
        result varchar(2000),
        primary key (id)
    );

    create table recipe (
        enabled boolean,
        print_count integer not null,
        create_time timestamp,
        id integer,
        packaging_standard_id bigint,
        process_template_id bigint,
        qc_template_id bigint,
        update_time timestamp,
        recipe_type varchar(20) not null,
        product_code varchar(30),
        recipe_no varchar(30) not null unique,
        category varchar(50),
        product_name varchar(100) not null,
        description varchar(500),
        primary key (id)
    );

    create table recipe_change_log (
        create_time timestamp,
        id integer,
        recipe_id bigint,
        version_id bigint,
        version_no varchar(10),
        action varchar(20),
        recipe_no varchar(30),
        operator varchar(50),
        product_name varchar(100),
        detail varchar(1000),
        primary key (id)
    );

    create table recipe_tree_node (
        qty numeric(14,3) not null,
        sort_order integer,
        id integer,
        parent_node_id bigint,
        ref_recipe_id bigint,
        version_id bigint not null,
        unit varchar(10),
        node_type varchar(20) not null,
        material_code varchar(30),
        category varchar(50),
        sub_category varchar(50),
        material_name varchar(100),
        spec varchar(100),
        remark varchar(200),
        primary key (id)
    );

    create table recipe_version (
        batch_qty numeric(14,3),
        effective_date date,
        create_time timestamp,
        id integer,
        recipe_id bigint not null,
        released_time timestamp,
        update_time timestamp,
        unit varchar(10),
        status varchar(20) not null,
        version_no varchar(20) not null,
        created_by varchar(50),
        released_by varchar(50),
        remark varchar(500),
        primary key (id)
    );

    create table return_order (
        qty numeric(14,3) not null,
        return_amount numeric(14,2),
        unit_price numeric(14,2),
        approve_time timestamp,
        create_time timestamp,
        customer_id bigint,
        id integer,
        ref_arrival_id bigint,
        supplier_id bigint,
        update_time timestamp,
        unit varchar(10),
        doc_no varchar(20) not null unique,
        inbound_doc_no varchar(20),
        location_id varchar(20),
        outbound_doc_no varchar(20),
        settle_type varchar(20),
        status varchar(20) not null,
        type varchar(20) not null,
        warehouse_id varchar(20),
        material_code varchar(30) not null,
        purchase_order_no varchar(30),
        qc_inspection_no varchar(30),
        ref_sales_outbound_no varchar(30),
        sales_order_no varchar(30),
        approved_by varchar(50),
        batch_no varchar(50),
        created_by varchar(50),
        location_name varchar(50),
        customer_name varchar(100),
        material_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table salary_item (
        base numeric(12,2),
        bonus numeric(12,2),
        deduction numeric(12,2),
        gross numeric(12,2) not null,
        income_tax numeric(12,2),
        net numeric(12,2) not null,
        piecework numeric(12,2),
        social_ins numeric(12,2),
        employee_id bigint not null,
        id integer,
        sheet_id bigint not null,
        dept varchar(20) not null,
        employee_name varchar(50) not null,
        primary key (id)
    );

    create table salary_sheet (
        total_gross numeric(14,2) not null,
        total_net numeric(14,2) not null,
        confirmed_time timestamp,
        create_time timestamp,
        id integer,
        update_time timestamp,
        period varchar(10) not null,
        doc_no varchar(20) not null unique,
        status varchar(20) not null,
        confirmed_by varchar(50),
        created_by varchar(50),
        remark varchar(500),
        primary key (id)
    );

    create table sales_order (
        credit_exceeded boolean,
        expected_ship_date date,
        order_date date,
        tax_rate numeric(5,2),
        total_amount numeric(14,2),
        create_time timestamp,
        customer_id bigint not null,
        id integer,
        update_time timestamp,
        contract_no varchar(20),
        order_no varchar(20) not null unique,
        source_warehouse_id varchar(20) not null,
        status varchar(20) not null,
        created_by varchar(50),
        customer_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table sales_order_change_log (
        create_time timestamp,
        id integer,
        order_id bigint not null,
        order_no varchar(20),
        operator varchar(50),
        detail varchar(2000) not null,
        primary key (id)
    );

    create table sales_order_item (
        amount numeric(14,2),
        qty numeric(14,3) not null,
        return_qty numeric(14,3),
        shipped_qty numeric(14,3),
        unit_price numeric(12,2),
        id integer,
        order_id bigint not null,
        unit varchar(10),
        material_code varchar(30) not null,
        material_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table sales_outbound (
        cost numeric(14,2),
        print_count integer not null,
        qty numeric(14,3) not null,
        unit_price numeric(14,2),
        create_time timestamp,
        id integer,
        update_time timestamp,
        unit varchar(10),
        location_id varchar(20),
        status varchar(20) not null,
        warehouse_id varchar(20) not null,
        batch_no varchar(30),
        doc_no varchar(30) not null unique,
        material_code varchar(30) not null,
        sales_order_no varchar(30),
        created_by varchar(50),
        customer_name varchar(100),
        material_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table sample_formula (
        est_cost numeric(14,2),
        total_qty numeric(14,3),
        converted_recipe_id bigint,
        converted_time timestamp,
        create_time timestamp,
        id integer,
        sample_request_id bigint not null unique,
        update_time timestamp,
        color_series varchar(10),
        main_material varchar(10),
        sub_category varchar(10),
        formula_no varchar(20) not null unique,
        sample_location varchar(20),
        material_code varchar(30),
        material_name varchar(100),
        primary key (id)
    );

    create table sample_formula_history (
        round integer,
        create_time timestamp,
        formula_id bigint not null,
        id integer,
        snapshot TEXT,
        primary key (id)
    );

    create table sample_formula_item (
        qty numeric(14,3) not null,
        sort_order integer,
        formula_id bigint not null,
        id integer,
        category varchar(10),
        sub_category varchar(10),
        unit varchar(10),
        material_code varchar(30) not null,
        material_name varchar(100),
        primary key (id)
    );

    create table sample_request (
        adjust_count integer,
        apply_date date,
        feedback_date date,
        qty numeric(14,3),
        send_date date,
        assign_time timestamp,
        create_time timestamp,
        customer_id bigint,
        id integer,
        rd_progress_id bigint,
        receive_time timestamp,
        ref_sample_id bigint,
        update_time timestamp,
        sample_size varchar(10),
        unit varchar(10),
        sample_no varchar(20) not null unique,
        status varchar(20) not null,
        won_order_no varchar(20),
        material_code varchar(30),
        applicant varchar(50),
        assignee varchar(50),
        colorist varchar(50),
        express_no varchar(50),
        customer_name varchar(100) not null,
        loss_reason varchar(200),
        color_note varchar(500),
        material_desc varchar(500) not null,
        remark varchar(500),
        feedback_content varchar(1000),
        primary key (id)
    );

    create table shipping_log (
        freight numeric(12,2) not null,
        create_time timestamp,
        id integer,
        update_time timestamp,
        ship_date varchar(10),
        borne varchar(20) not null,
        doc_no varchar(20) not null unique,
        outbound_doc_no varchar(30),
        sales_order_no varchar(30) not null,
        carrier varchar(50),
        created_by varchar(50),
        customer_name varchar(50),
        tracking_no varchar(50),
        remark varchar(500),
        primary key (id)
    );

    create table stat_finance_summary (
        ap_paid numeric(16,2) not null,
        ap_total numeric(16,2) not null,
        ar_received numeric(16,2) not null,
        ar_total numeric(16,2) not null,
        id bigint not null,
        update_time timestamp,
        primary key (id)
    );

    create table stat_inventory_daily (
        in_amount numeric(16,2) not null,
        in_qty numeric(14,3) not null,
        out_qty numeric(14,3) not null,
        id integer,
        stat_date varchar(10) not null,
        warehouse_id varchar(20) not null,
        material_code varchar(30) not null,
        primary key (id)
    );

    create table stat_material_usage (
        out_qty numeric(14,3) not null,
        usage_days integer not null,
        period varchar(7) not null,
        id integer,
        warehouse_id varchar(20),
        material_code varchar(30) not null,
        primary key (id)
    );

    create table stat_order_monthly (
        order_count integer not null,
        total_amount numeric(16,2) not null,
        period varchar(7) not null,
        id integer,
        order_type varchar(20) not null,
        primary key (id)
    );

    create table stock_check (
        actual_qty numeric(14,3),
        adjust_qty numeric(14,3),
        diff_qty numeric(14,3),
        system_qty numeric(14,3),
        create_time timestamp,
        id integer,
        doc_type varchar(20) not null,
        from_location_id varchar(20),
        status varchar(20) not null,
        to_location_id varchar(20),
        warehouse_id varchar(20) not null,
        batch_no varchar(30),
        doc_no varchar(30) not null unique,
        material_code varchar(30) not null,
        from_location_name varchar(50),
        operator varchar(50),
        to_location_name varchar(50),
        material_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table supplier (
        blacklisted boolean,
        enabled boolean,
        processing_fee numeric(38,2),
        create_time timestamp,
        id integer,
        update_time timestamp,
        code varchar(20) not null unique,
        payment_method varchar(20),
        type varchar(20),
        name varchar(100) not null,
        payment_terms varchar(500),
        primary key (id)
    );

    create table supplier_quality_trace (
        arrival_date date,
        compensation_amount numeric(14,2),
        issue_date date,
        loss_amount numeric(38,2),
        print_count integer not null,
        purchase_amount numeric(38,2),
        purchase_qty numeric(38,2),
        purchase_unit_price numeric(38,2),
        resolve_date date,
        create_time timestamp,
        id integer,
        supplier_id bigint,
        update_time timestamp,
        category varchar(20),
        order_category varchar(20),
        qc_inspection_no varchar(20),
        qc_status varchar(20),
        status varchar(20) not null,
        trace_no varchar(20) not null unique,
        batch_no varchar(30) not null,
        material_code varchar(30),
        purchase_order_no varchar(30),
        result_type varchar(30),
        created_by varchar(50),
        handler varchar(50),
        material_name varchar(100),
        supplier_name varchar(100) not null,
        remark varchar(500),
        result_remark varchar(1000),
        description varchar(2000) not null,
        primary key (id)
    );

    create table sys_role (
        enabled boolean,
        create_time timestamp,
        id integer,
        update_time timestamp,
        code varchar(30) not null unique,
        name varchar(50) not null,
        primary key (id)
    );

    create table sys_role_permission (
        id integer,
        perm_code varchar(30) not null,
        role_code varchar(30) not null,
        primary key (id)
    );

    create table sys_user (
        enabled boolean,
        must_change_pwd boolean,
        create_time timestamp,
        id integer,
        update_time timestamp,
        phone varchar(20),
        role varchar(30) not null,
        real_name varchar(50) not null,
        username varchar(50) not null unique,
        password varchar(100) not null,
        primary key (id)
    );

    create table task (
        due_date date,
        completed_at timestamp,
        create_time timestamp,
        id integer,
        update_time timestamp,
        priority varchar(10) not null,
        status varchar(20) not null,
        doc_no varchar(25) not null unique,
        created_by varchar(50),
        owner varchar(50) not null,
        title varchar(200) not null,
        collaborators varchar(500),
        remark varchar(500),
        completed_note varchar(1000),
        description varchar(2000),
        primary key (id)
    );

    create table task_progress (
        create_time timestamp,
        id integer,
        task_id bigint not null,
        action_type varchar(20) not null,
        reporter varchar(50) not null,
        content varchar(1000),
        primary key (id)
    );

    create table voucher (
        attachment_count integer,
        total_credit numeric(14,2) not null,
        total_debit numeric(14,2) not null,
        voucher_date date not null,
        create_time timestamp,
        id integer,
        posted_time timestamp,
        update_time timestamp,
        period varchar(10) not null,
        doc_no varchar(20) not null unique,
        source varchar(20) not null,
        status varchar(20) not null,
        ref_doc_no varchar(30),
        created_by varchar(50),
        posted_by varchar(50),
        remark varchar(500),
        primary key (id)
    );

    create table voucher_entry (
        credit numeric(14,2),
        debit numeric(14,2),
        line_no integer,
        id integer,
        voucher_id bigint not null,
        aux_type varchar(20),
        subject_code varchar(20) not null,
        aux_name varchar(100),
        subject_name varchar(100) not null,
        digest varchar(200),
        primary key (id)
    );

    create table warehouse (
        enabled boolean,
        create_time timestamp,
        id integer,
        update_time timestamp,
        code varchar(20) not null unique,
        contact_phone varchar(20),
        processor_id varchar(20),
        warehouse_type varchar(20) not null,
        contact_person varchar(50),
        name varchar(50) not null,
        processor_name varchar(100),
        address varchar(200),
        remark varchar(500),
        primary key (id)
    );

    create table warehouse_location (
        enabled boolean,
        sort_order integer,
        create_time timestamp,
        id integer,
        update_time timestamp,
        zone_id bigint not null,
        code varchar(20) not null,
        name varchar(50) not null,
        remark varchar(500),
        primary key (id)
    );

    create table warehouse_zone (
        enabled boolean,
        sort_order integer,
        create_time timestamp,
        id integer,
        update_time timestamp,
        warehouse_id bigint not null,
        code varchar(20) not null,
        zone_type varchar(20),
        name varchar(50) not null,
        remark varchar(500),
        primary key (id)
    );

    create table weekly_topic (
        closed_date date,
        plan_date date,
        create_time timestamp,
        id integer,
        update_time timestamp,
        category varchar(20) not null,
        created_by varchar(50),
        owner varchar(50) not null,
        content varchar(2000) not null,
        result varchar(2000),
        primary key (id)
    );

    create table account_mapping (
        id integer,
        update_time timestamp,
        subject_code varchar(20) not null,
        map_key varchar(60) not null unique,
        remark varchar(200),
        primary key (id)
    );

    create table account_period (
        closed boolean not null,
        close_time timestamp,
        id integer,
        period varchar(10) not null unique,
        closed_by varchar(50),
        primary key (id)
    );

    create table account_subject (
        opening_balance numeric(14,2),
        direction varchar(5) not null,
        opening_direction varchar(5),
        create_time timestamp,
        id integer,
        update_time timestamp,
        category varchar(20) not null,
        code varchar(20) not null unique,
        parent_code varchar(20),
        status varchar(20) not null,
        name varchar(100) not null,
        primary key (id)
    );

    create table accounts_payable (
        amount numeric(14,2) not null,
        due_date date,
        paid_amount numeric(14,2),
        arrival_id bigint,
        create_time timestamp,
        id integer,
        supplier_id bigint not null,
        update_time timestamp,
        doc_no varchar(20) not null unique,
        outsource_order_no varchar(20),
        payable_type varchar(20) not null,
        purchase_order_no varchar(20),
        status varchar(20) not null,
        remark varchar(500),
        primary key (id)
    );

    create table accounts_receivable (
        amount numeric(14,2) not null,
        due_date date,
        received_amount numeric(14,2),
        create_time timestamp,
        customer_id bigint not null,
        id integer,
        update_time timestamp,
        contract_no varchar(20),
        doc_no varchar(20) not null unique,
        sales_order_no varchar(20),
        status varchar(20) not null,
        remark varchar(500),
        primary key (id)
    );

    create table advance_payment (
        amount numeric(14,2) not null,
        pay_date date,
        used_amount numeric(14,2) not null,
        create_time timestamp,
        id integer,
        partner_id bigint,
        update_time timestamp,
        direction varchar(20) not null,
        doc_no varchar(20) not null unique,
        method varchar(20),
        status varchar(20) not null,
        created_by varchar(50),
        partner_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table asset (
        original_value numeric(14,2) not null,
        purchase_date date,
        residual_rate numeric(5,2),
        scrap_date date,
        useful_life_months integer not null,
        create_time timestamp,
        id integer,
        update_time timestamp,
        category varchar(20) not null,
        doc_no varchar(20) not null unique,
        expense_subject varchar(20) not null,
        status varchar(20) not null,
        keeper varchar(50),
        location varchar(100),
        name varchar(100) not null,
        remark varchar(500),
        primary key (id)
    );

    create table asset_depreciation (
        amount numeric(14,2) not null,
        asset_id bigint not null,
        create_time timestamp,
        id integer,
        voucher_id bigint,
        period varchar(10) not null,
        expense_subject varchar(20) not null,
        asset_name varchar(100) not null,
        primary key (id)
    );

    create table bank_account (
        opening_balance numeric(16,2),
        create_time timestamp,
        id integer,
        enabled varchar(10),
        account_no varchar(30),
        name varchar(50) not null,
        bank_name varchar(100),
        primary key (id)
    );

    create table bank_statement (
        amount numeric(16,2) not null,
        balance numeric(16,2),
        tx_date date not null,
        account_id bigint not null,
        create_time timestamp,
        id integer,
        ref_id bigint,
        status varchar(10),
        ref_type varchar(20),
        import_batch varchar(30),
        counterparty varchar(100),
        summary varchar(200),
        primary key (id)
    );

    create table coding_rule (
        current_seq integer not null,
        enabled boolean,
        number_start integer not null,
        create_time timestamp,
        id integer,
        update_time timestamp,
        category_code varchar(10) not null,
        sub_category_code varchar(10) not null,
        category varchar(50) not null,
        sub_category varchar(50) not null,
        primary key (id)
    );

    create table crm_contact (
        is_primary boolean,
        create_time timestamp,
        customer_id bigint,
        id integer,
        update_time timestamp,
        phone varchar(30),
        name varchar(50) not null,
        title varchar(50),
        wechat varchar(50),
        company_name varchar(100) not null,
        email varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table crm_follow_up (
        follow_date date,
        next_date date,
        create_time timestamp,
        customer_id bigint,
        id integer,
        opportunity_id bigint,
        method varchar(20) not null,
        operator varchar(50),
        content varchar(2000) not null,
        primary key (id)
    );

    create table crm_opportunity (
        expect_amount numeric(14,2),
        expect_date date,
        create_time timestamp,
        customer_id bigint,
        id integer,
        update_time timestamp,
        stage varchar(20) not null,
        won_order_no varchar(20),
        created_by varchar(50),
        owner varchar(50),
        company_name varchar(100) not null,
        title varchar(100) not null,
        loss_reason varchar(200),
        product_interest varchar(200),
        remark varchar(500),
        primary key (id)
    );

    create table customer (
        blacklisted boolean,
        credit_limit numeric(14,2),
        enabled boolean,
        create_time timestamp,
        id integer,
        update_time timestamp,
        abc_level varchar(10),
        code varchar(20) not null unique,
        contact_phone varchar(20),
        payment_method varchar(20),
        tax_no varchar(30),
        bank_account varchar(50),
        contact_person varchar(50),
        legal_person varchar(50),
        bank_name varchar(100),
        name varchar(100) not null,
        address varchar(200),
        payment_terms varchar(500),
        remark varchar(500),
        primary key (id)
    );

    create table customer_complaint (
        close_date date,
        complaint_date date,
        resolve_date date,
        create_time timestamp,
        customer_id bigint,
        id integer,
        update_time timestamp,
        category varchar(20),
        complaint_no varchar(20) not null unique,
        qc_doc_no varchar(20),
        sales_order_no varchar(20),
        status varchar(20) not null,
        batch_no varchar(30),
        material_code varchar(30),
        created_by varchar(50),
        handler varchar(50),
        customer_name varchar(100) not null,
        material_name varchar(100),
        remark varchar(500),
        action varchar(1000),
        cause varchar(1000),
        description varchar(2000) not null,
        primary key (id)
    );

    create table dict_item (
        enabled boolean,
        sort_order integer,
        create_time timestamp,
        id integer,
        type varchar(50) not null,
        value varchar(100) not null,
        label varchar(200),
        primary key (id)
    );

    create table employee (
        base_salary numeric(12,2),
        hire_date date,
        leave_date date,
        create_time timestamp,
        id integer,
        update_time timestamp,
        dept varchar(20) not null,
        status varchar(20) not null,
        phone varchar(30),
        bank_card varchar(50),
        name varchar(50) not null,
        position varchar(50),
        remark varchar(500),
        primary key (id)
    );

    create table expense (
        amount numeric(14,2) not null,
        occur_date date,
        create_time timestamp,
        id integer,
        update_time timestamp,
        direction varchar(20) not null,
        doc_no varchar(20) not null unique,
        method varchar(20),
        created_by varchar(50),
        expense_type varchar(50) not null,
        handler varchar(50),
        partner varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table finished_product_purchase (
        is_free boolean,
        purchase_date date,
        qty numeric(14,3),
        received_qty numeric(14,3),
        tax_rate numeric(5,2),
        total_amount numeric(14,2),
        unit_price numeric(12,2),
        create_time timestamp,
        id integer,
        supplier_id bigint,
        update_time timestamp,
        status varchar(20),
        material_code varchar(30),
        order_no varchar(30) not null unique,
        warehouse_id varchar(30),
        brand varchar(50),
        created_by varchar(50),
        material_name varchar(100),
        supplier_name varchar(100),
        file_path varchar(200),
        remark varchar(500),
        primary key (id)
    );

    create table inventory_ledger (
        amount numeric(14,2),
        available_qty numeric(14,3),
        expiry_date date,
        in_transit_qty numeric(14,3),
        inbound_date date,
        occupied_qty numeric(14,3),
        produce_date date,
        qc_date date,
        qty numeric(14,3),
        unit_price numeric(14,2),
        create_time timestamp,
        id integer,
        last_update_time timestamp,
        unit varchar(10),
        location_id varchar(20),
        ownership_type varchar(20) not null,
        qc_status varchar(20),
        warehouse_id varchar(20) not null,
        batch_no varchar(30),
        material_code varchar(30) not null,
        qc_inspection_no varchar(30),
        location_name varchar(50),
        qc_inspector varchar(50),
        zone_name varchar(50),
        material_name varchar(100),
        qc_result varchar(500),
        primary key (id)
    );

    create table inventory_movement (
        qty numeric(14,3) not null,
        qty_after numeric(14,3),
        qty_before numeric(14,3),
        direction varchar(5) not null,
        create_time timestamp,
        id integer,
        doc_type varchar(20) not null,
        location_id varchar(20),
        ownership_type varchar(20) not null,
        warehouse_id varchar(20) not null,
        batch_no varchar(30),
        doc_no varchar(30) not null,
        material_code varchar(30) not null,
        operator varchar(50),
        remark varchar(500),
        primary key (id)
    );

    create table invoice (
        amount numeric(14,2) not null,
        invoice_date date,
        tax_amount numeric(14,2),
        tax_rate integer,
        total_amount numeric(14,2),
        create_time timestamp,
        id integer,
        partner_id bigint,
        update_time timestamp,
        direction varchar(20) not null,
        doc_no varchar(20) not null unique,
        flush_doc_no varchar(20),
        partner_type varchar(20) not null,
        status varchar(20) not null,
        invoice_no varchar(30),
        partner_tax_no varchar(30),
        ref_order_no varchar(30),
        created_by varchar(50),
        partner_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table loss_letter_template (
        enabled boolean not null,
        is_default boolean not null,
        create_time timestamp,
        id integer,
        update_time timestamp,
        name varchar(50) not null,
        closing_text varchar(1000),
        opening_text varchar(1000),
        require_text varchar(1000),
        body_text varchar(2000) not null,
        primary key (id)
    );

    create table material (
        enabled boolean,
        shelf_life_days integer,
        create_time timestamp,
        id integer,
        update_time timestamp,
        color_series varchar(20),
        main_material varchar(20),
        code varchar(30) not null unique,
        brand varchar(50),
        category varchar(50),
        sub_category varchar(50),
        brand_owner varchar(100),
        name varchar(100) not null,
        alternative_codes varchar(500),
        primary key (id)
    );

    create table other_inbound (
        finance_amount numeric(14,2),
        gen_finance boolean,
        price numeric(12,4),
        qty numeric(14,3) not null,
        return_amount numeric(14,2),
        tax_rate numeric(5,2),
        create_time timestamp,
        customer_id bigint,
        finance_partner_id bigint,
        id integer,
        return_ref_id bigint,
        supplier_id bigint,
        update_time timestamp,
        unit varchar(10),
        doc_no varchar(20) not null unique,
        finance_doc_no varchar(20),
        location_id varchar(20),
        reason varchar(20),
        return_offset_status varchar(20),
        return_ref_type varchar(20),
        status varchar(20) not null,
        warehouse_id varchar(20) not null,
        batch_no varchar(30),
        material_code varchar(30) not null,
        return_ref_doc_no varchar(30),
        created_by varchar(50),
        finance_partner_name varchar(50),
        material_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table other_outbound (
        cost numeric(14,2),
        finance_amount numeric(14,2),
        gen_finance boolean,
        qty numeric(14,3) not null,
        unit_price numeric(14,2),
        create_time timestamp,
        finance_partner_id bigint,
        id integer,
        return_order_id bigint,
        update_time timestamp,
        unit varchar(10),
        doc_no varchar(20) not null unique,
        finance_doc_no varchar(20),
        location_id varchar(20),
        reason varchar(20),
        return_ref_doc_no varchar(20),
        status varchar(20) not null,
        warehouse_id varchar(20) not null,
        batch_no varchar(30),
        material_code varchar(30) not null,
        created_by varchar(50),
        finance_partner_name varchar(50),
        material_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table outsource_finish_inbound (
        qty numeric(14,3) not null,
        theoretical_qty numeric(14,3),
        yield_rate numeric(8,2),
        create_time timestamp,
        id integer,
        update_time timestamp,
        unit varchar(10),
        doc_no varchar(20) not null unique,
        location_id varchar(20),
        outsource_order_no varchar(20) not null,
        status varchar(20) not null,
        warehouse_id varchar(20) not null,
        batch_no varchar(30),
        created_by varchar(50),
        location_name varchar(50),
        product_code varchar(50),
        zone_name varchar(50),
        product_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table outsource_material_consume (
        consume_qty numeric(14,3) not null,
        remain_qty numeric(14,3),
        id integer,
        unit varchar(10),
        inbound_doc_no varchar(20) not null,
        outsource_order_no varchar(20) not null,
        batch_no varchar(30),
        material_code varchar(30) not null,
        remark varchar(200),
        primary key (id)
    );

    create table outsource_material_outbound (
        cost numeric(14,2),
        qty numeric(14,3) not null,
        signed_diff numeric(14,3),
        signed_qty numeric(14,3),
        unit_price numeric(14,2),
        create_time timestamp,
        id integer,
        update_time timestamp,
        unit varchar(10),
        doc_no varchar(20) not null unique,
        from_warehouse_id varchar(20) not null,
        location_id varchar(20),
        outsource_order_no varchar(20) not null,
        processor_id varchar(20) not null,
        status varchar(20) not null,
        to_warehouse_id varchar(20),
        batch_no varchar(30),
        material_code varchar(30) not null,
        created_by varchar(50),
        location_name varchar(50),
        zone_name varchar(50),
        processor_name varchar(100),
        signed_diff_reason varchar(200),
        remark varchar(500),
        primary key (id)
    );

    create table outsource_order (
        batch_qty numeric(14,3) not null,
        print_count integer not null,
        processing_fee numeric(12,2),
        schedule_seq integer,
        create_time timestamp,
        id integer,
        recipe_version_id bigint,
        supplier_id bigint,
        update_time timestamp,
        unit varchar(10),
        order_no varchar(20) not null unique,
        sales_order_no varchar(20),
        status varchar(20) not null,
        created_by varchar(50),
        product_code varchar(50),
        processor varchar(100),
        product_name varchar(100) not null,
        remark varchar(500),
        primary key (id)
    );

    create table outsource_order_item (
        qty numeric(14,3) not null,
        id integer,
        order_id bigint not null,
        ref_recipe_id bigint,
        unit varchar(10),
        node_type varchar(20),
        material_code varchar(30) not null,
        material_name varchar(100),
        spec varchar(100),
        remark varchar(200),
        primary key (id)
    );

    create table packaging_standard (
        capacity_kg numeric(10,3),
        enabled boolean,
        unit_price numeric(14,2) not null,
        create_time timestamp,
        id integer,
        update_time timestamp,
        pack_type varchar(20) not null,
        name varchar(100) not null,
        spec varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table packaging_standard_item (
        qty numeric(10,3) not null,
        sort_order integer,
        unit_price numeric(14,2) not null,
        id integer,
        packaging_id bigint not null,
        pack_type varchar(20),
        name varchar(100) not null,
        spec varchar(100),
        primary key (id)
    );

    create table payment_disbursement (
        amount numeric(14,2) not null,
        pay_date date not null,
        ap_id bigint,
        create_time timestamp,
        id integer,
        supplier_id bigint not null,
        ap_doc_no varchar(20),
        doc_no varchar(20) not null unique,
        method varchar(20) not null,
        bank_account varchar(50),
        operator varchar(50),
        supplier_name varchar(50),
        remark varchar(500),
        primary key (id)
    );

    create table payment_receipt (
        amount numeric(14,2) not null,
        receipt_date date not null,
        ar_id bigint,
        create_time timestamp,
        customer_id bigint not null,
        id integer,
        ar_doc_no varchar(20),
        doc_no varchar(20) not null unique,
        method varchar(20) not null,
        bank_account varchar(50),
        customer_name varchar(50),
        operator varchar(50),
        remark varchar(500),
        primary key (id)
    );

    create table price_policy (
        effective_date date not null,
        expiry_date date,
        min_qty numeric(14,3),
        unit_price numeric(14,2) not null,
        material_category varchar(5),
        create_time timestamp,
        id integer,
        status varchar(10),
        material_code varchar(30),
        created_by varchar(50),
        remark varchar(200),
        primary key (id)
    );

    create table process_qc_item (
        sort_order integer,
        test_times integer,
        id integer,
        stage_id bigint not null,
        unit varchar(20),
        name varchar(50) not null,
        standard varchar(100),
        method varchar(200),
        primary key (id)
    );

    create table process_stage (
        sort_order integer,
        id integer,
        template_id bigint not null,
        stage_no varchar(10),
        role_hint varchar(50),
        stage_name varchar(50) not null,
        primary key (id)
    );

    create table process_step (
        sort_order integer,
        id integer,
        stage_id bigint not null,
        step_code varchar(10),
        params varchar(200),
        description varchar(1000),
        primary key (id)
    );

    create table process_template (
        enabled boolean,
        is_default boolean not null,
        create_time timestamp,
        id integer,
        update_time timestamp,
        recipe_type varchar(20) not null,
        created_by varchar(50),
        name varchar(50) not null,
        packing_requirement varchar(1000),
        primary key (id)
    );

    create table production_inbound (
        qty numeric(14,3) not null,
        theoretical_qty numeric(14,3),
        yield_rate numeric(8,2),
        create_time timestamp,
        id integer,
        update_time timestamp,
        unit varchar(10),
        doc_no varchar(20) not null unique,
        location_id varchar(20),
        production_order_no varchar(20),
        status varchar(20) not null,
        warehouse_id varchar(20) not null,
        batch_no varchar(30),
        created_by varchar(50),
        location_name varchar(50),
        product_code varchar(50),
        zone_name varchar(50),
        product_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table production_order (
        batch_qty numeric(14,3) not null,
        print_count integer not null,
        schedule_seq integer,
        create_time timestamp,
        id integer,
        recipe_version_id bigint,
        update_time timestamp,
        unit varchar(10),
        order_no varchar(20) not null unique,
        sales_order_no varchar(20),
        status varchar(20) not null,
        created_by varchar(50),
        product_code varchar(50),
        product_name varchar(100) not null,
        remark varchar(500),
        primary key (id)
    );

    create table production_order_exception (
        input_qty numeric(14,3),
        io_ratio numeric(8,2),
        output_qty numeric(14,3),
        closed_time timestamp,
        create_time timestamp,
        id integer,
        update_time timestamp,
        order_no varchar(20) not null unique,
        status varchar(20) not null,
        created_by varchar(50),
        handler varchar(50),
        reason varchar(200),
        measure varchar(500),
        remark varchar(500),
        primary key (id)
    );

    create table production_order_item (
        qty numeric(14,3) not null,
        id integer,
        order_id bigint not null,
        ref_recipe_id bigint,
        unit varchar(10),
        node_type varchar(20),
        material_code varchar(30) not null,
        material_name varchar(100),
        spec varchar(100),
        remark varchar(200),
        primary key (id)
    );

    create table production_outbound (
        cost numeric(14,2),
        qty numeric(14,3) not null,
        unit_price numeric(14,2),
        create_time timestamp,
        id integer,
        update_time timestamp,
        unit varchar(10),
        doc_no varchar(20) not null unique,
        doc_type varchar(20) not null,
        location_id varchar(20),
        production_order_no varchar(20),
        status varchar(20) not null,
        supplement_type varchar(20),
        warehouse_id varchar(20) not null,
        batch_no varchar(30),
        material_code varchar(30) not null,
        created_by varchar(50),
        location_name varchar(50),
        zone_name varchar(50),
        material_name varchar(100),
        product_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table purchase_arrival (
        arrival_date date,
        qty numeric(14,3),
        tax_rate numeric(5,2),
        unit_price numeric(14,4),
        create_time timestamp,
        id integer,
        supplier_id bigint,
        type varchar(10) not null,
        unit varchar(10),
        location_id varchar(20),
        status varchar(20) not null,
        warehouse_id varchar(20),
        batch_no varchar(30),
        doc_no varchar(30),
        material_code varchar(30),
        ref_order_no varchar(30) not null,
        location_name varchar(50),
        zone_name varchar(50),
        material_name varchar(100),
        operator varchar(100),
        supplier_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table purchase_order (
        expected_delivery_date date,
        order_date date,
        total_amount numeric(14,2),
        create_time timestamp,
        id integer,
        supplier_id bigint,
        update_time timestamp,
        order_no varchar(20) not null unique,
        status varchar(20) not null,
        target_warehouse_id varchar(20),
        created_by varchar(50),
        payment_terms varchar(200),
        remark varchar(500),
        primary key (id)
    );

    create table purchase_order_item (
        amount numeric(14,2),
        qty numeric(14,3) not null,
        received_qty numeric(14,3),
        return_qty numeric(14,3),
        unit_price numeric(12,2),
        id integer,
        order_id bigint not null,
        unit varchar(10),
        material_code varchar(30) not null,
        remark varchar(500),
        primary key (id)
    );

    create table qc_template (
        enabled boolean,
        is_default boolean,
        create_time timestamp,
        id integer,
        update_time timestamp,
        apply_category varchar(10) not null,
        color_series varchar(20),
        main_material varchar(20),
        created_by varchar(50),
        sub_category varchar(50),
        name varchar(100) not null,
        remark varchar(500),
        primary key (id)
    );

    create table qc_template_item (
        sort_order integer,
        id integer,
        template_id bigint not null,
        unit varchar(30),
        name varchar(100) not null,
        method varchar(200),
        standard varchar(200),
        primary key (id)
    );

    create table quality_inspection (
        inspect_date date,
        print_count integer not null,
        produce_date date,
        qty numeric(14,3),
        unit_price numeric(14,2),
        material_category varchar(5),
        arrival_id bigint,
        create_time timestamp,
        id integer,
        update_time timestamp,
        unit varchar(10),
        location_id varchar(20),
        status varchar(20) not null,
        type varchar(20) not null,
        warehouse_id varchar(20),
        batch_no varchar(30),
        inspection_no varchar(30) not null unique,
        material_code varchar(30),
        ref_doc_no varchar(30),
        ref_doc_type varchar(30),
        created_by varchar(50),
        inspector varchar(50),
        material_name varchar(100),
        remark varchar(500),
        result_remark varchar(500),
        primary key (id)
    );

    create table quality_inspection_item (
        sort_order integer,
        create_time timestamp,
        id integer,
        inspection_id bigint not null,
        template_id bigint,
        update_time timestamp,
        item_result varchar(20),
        unit varchar(30),
        name varchar(100) not null,
        measured_value varchar(200),
        method varchar(200),
        standard varchar(200),
        primary key (id)
    );

    create table quotation (
        quote_date date,
        total_amount numeric(14,2),
        valid_until date,
        create_time timestamp,
        customer_id bigint not null,
        id integer,
        update_time timestamp,
        quote_no varchar(20) not null unique,
        sales_order_no varchar(20),
        status varchar(20) not null,
        created_by varchar(50),
        customer_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table quotation_item (
        amount numeric(14,2),
        qty numeric(14,3) not null,
        unit_price numeric(12,2),
        id integer,
        quotation_id bigint not null,
        unit varchar(10),
        material_code varchar(30) not null,
        material_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table raw_material_purchase (
        increase_amount numeric(12,2),
        increase_rate numeric(8,2),
        is_free boolean,
        last_unit_price numeric(12,2),
        purchase_date date,
        qty numeric(14,3),
        received_qty numeric(14,3),
        tax_rate numeric(5,2),
        total_amount numeric(14,2),
        unit_price numeric(12,2),
        create_time timestamp,
        id integer,
        supplier_id bigint,
        update_time timestamp,
        status varchar(20),
        category varchar(30),
        material_code varchar(30),
        order_no varchar(30) not null,
        sub_category varchar(30),
        warehouse_id varchar(30),
        brand varchar(50),
        created_by varchar(50),
        supplier_name varchar(50),
        manufacturer varchar(100),
        material_name varchar(100),
        file_path varchar(200),
        remark varchar(500),
        primary key (id)
    );

    create table rd_progress (
        closed_date date,
        next_date date,
        raise_date date,
        create_time timestamp,
        id integer,
        update_time timestamp,
        category varchar(20) not null,
        created_by varchar(50),
        owner varchar(50) not null,
        progress varchar(500),
        content varchar(2000) not null,
        result varchar(2000),
        primary key (id)
    );

    create table recipe (
        enabled boolean,
        print_count integer not null,
        create_time timestamp,
        id integer,
        packaging_standard_id bigint,
        process_template_id bigint,
        qc_template_id bigint,
        update_time timestamp,
        recipe_type varchar(20) not null,
        product_code varchar(30),
        recipe_no varchar(30) not null unique,
        category varchar(50),
        product_name varchar(100) not null,
        description varchar(500),
        primary key (id)
    );

    create table recipe_change_log (
        create_time timestamp,
        id integer,
        recipe_id bigint,
        version_id bigint,
        version_no varchar(10),
        action varchar(20),
        recipe_no varchar(30),
        operator varchar(50),
        product_name varchar(100),
        detail varchar(1000),
        primary key (id)
    );

    create table recipe_tree_node (
        qty numeric(14,3) not null,
        sort_order integer,
        id integer,
        parent_node_id bigint,
        ref_recipe_id bigint,
        version_id bigint not null,
        unit varchar(10),
        node_type varchar(20) not null,
        material_code varchar(30),
        category varchar(50),
        sub_category varchar(50),
        material_name varchar(100),
        spec varchar(100),
        remark varchar(200),
        primary key (id)
    );

    create table recipe_version (
        batch_qty numeric(14,3),
        effective_date date,
        create_time timestamp,
        id integer,
        recipe_id bigint not null,
        released_time timestamp,
        update_time timestamp,
        unit varchar(10),
        status varchar(20) not null,
        version_no varchar(20) not null,
        created_by varchar(50),
        released_by varchar(50),
        remark varchar(500),
        primary key (id)
    );

    create table return_order (
        qty numeric(14,3) not null,
        return_amount numeric(14,2),
        unit_price numeric(14,2),
        approve_time timestamp,
        create_time timestamp,
        customer_id bigint,
        id integer,
        ref_arrival_id bigint,
        supplier_id bigint,
        update_time timestamp,
        unit varchar(10),
        doc_no varchar(20) not null unique,
        inbound_doc_no varchar(20),
        location_id varchar(20),
        outbound_doc_no varchar(20),
        settle_type varchar(20),
        status varchar(20) not null,
        type varchar(20) not null,
        warehouse_id varchar(20),
        material_code varchar(30) not null,
        purchase_order_no varchar(30),
        qc_inspection_no varchar(30),
        ref_sales_outbound_no varchar(30),
        sales_order_no varchar(30),
        approved_by varchar(50),
        batch_no varchar(50),
        created_by varchar(50),
        location_name varchar(50),
        customer_name varchar(100),
        material_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table salary_item (
        base numeric(12,2),
        bonus numeric(12,2),
        deduction numeric(12,2),
        gross numeric(12,2) not null,
        income_tax numeric(12,2),
        net numeric(12,2) not null,
        piecework numeric(12,2),
        social_ins numeric(12,2),
        employee_id bigint not null,
        id integer,
        sheet_id bigint not null,
        dept varchar(20) not null,
        employee_name varchar(50) not null,
        primary key (id)
    );

    create table salary_sheet (
        total_gross numeric(14,2) not null,
        total_net numeric(14,2) not null,
        confirmed_time timestamp,
        create_time timestamp,
        id integer,
        update_time timestamp,
        period varchar(10) not null,
        doc_no varchar(20) not null unique,
        status varchar(20) not null,
        confirmed_by varchar(50),
        created_by varchar(50),
        remark varchar(500),
        primary key (id)
    );

    create table sales_order (
        credit_exceeded boolean,
        expected_ship_date date,
        order_date date,
        tax_rate numeric(5,2),
        total_amount numeric(14,2),
        create_time timestamp,
        customer_id bigint not null,
        id integer,
        update_time timestamp,
        contract_no varchar(20),
        order_no varchar(20) not null unique,
        source_warehouse_id varchar(20) not null,
        status varchar(20) not null,
        created_by varchar(50),
        customer_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table sales_order_change_log (
        create_time timestamp,
        id integer,
        order_id bigint not null,
        order_no varchar(20),
        operator varchar(50),
        detail varchar(2000) not null,
        primary key (id)
    );

    create table sales_order_item (
        amount numeric(14,2),
        qty numeric(14,3) not null,
        return_qty numeric(14,3),
        shipped_qty numeric(14,3),
        unit_price numeric(12,2),
        id integer,
        order_id bigint not null,
        unit varchar(10),
        material_code varchar(30) not null,
        material_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table sales_outbound (
        cost numeric(14,2),
        print_count integer not null,
        qty numeric(14,3) not null,
        unit_price numeric(14,2),
        create_time timestamp,
        id integer,
        update_time timestamp,
        unit varchar(10),
        location_id varchar(20),
        status varchar(20) not null,
        warehouse_id varchar(20) not null,
        batch_no varchar(30),
        doc_no varchar(30) not null unique,
        material_code varchar(30) not null,
        sales_order_no varchar(30),
        created_by varchar(50),
        customer_name varchar(100),
        material_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table sample_formula (
        est_cost numeric(14,2),
        total_qty numeric(14,3),
        converted_recipe_id bigint,
        converted_time timestamp,
        create_time timestamp,
        id integer,
        sample_request_id bigint not null unique,
        update_time timestamp,
        color_series varchar(10),
        main_material varchar(10),
        sub_category varchar(10),
        formula_no varchar(20) not null unique,
        sample_location varchar(20),
        material_code varchar(30),
        material_name varchar(100),
        primary key (id)
    );

    create table sample_formula_history (
        round integer,
        create_time timestamp,
        formula_id bigint not null,
        id integer,
        snapshot TEXT,
        primary key (id)
    );

    create table sample_formula_item (
        qty numeric(14,3) not null,
        sort_order integer,
        formula_id bigint not null,
        id integer,
        category varchar(10),
        sub_category varchar(10),
        unit varchar(10),
        material_code varchar(30) not null,
        material_name varchar(100),
        primary key (id)
    );

    create table sample_request (
        adjust_count integer,
        apply_date date,
        feedback_date date,
        qty numeric(14,3),
        send_date date,
        assign_time timestamp,
        create_time timestamp,
        customer_id bigint,
        id integer,
        rd_progress_id bigint,
        receive_time timestamp,
        ref_sample_id bigint,
        update_time timestamp,
        sample_size varchar(10),
        unit varchar(10),
        sample_no varchar(20) not null unique,
        status varchar(20) not null,
        won_order_no varchar(20),
        material_code varchar(30),
        applicant varchar(50),
        assignee varchar(50),
        colorist varchar(50),
        express_no varchar(50),
        customer_name varchar(100) not null,
        loss_reason varchar(200),
        color_note varchar(500),
        material_desc varchar(500) not null,
        remark varchar(500),
        feedback_content varchar(1000),
        primary key (id)
    );

    create table shipping_log (
        freight numeric(12,2) not null,
        create_time timestamp,
        id integer,
        update_time timestamp,
        ship_date varchar(10),
        borne varchar(20) not null,
        doc_no varchar(20) not null unique,
        outbound_doc_no varchar(30),
        sales_order_no varchar(30) not null,
        carrier varchar(50),
        created_by varchar(50),
        customer_name varchar(50),
        tracking_no varchar(50),
        remark varchar(500),
        primary key (id)
    );

    create table stat_finance_summary (
        ap_paid numeric(16,2) not null,
        ap_total numeric(16,2) not null,
        ar_received numeric(16,2) not null,
        ar_total numeric(16,2) not null,
        id bigint not null,
        update_time timestamp,
        primary key (id)
    );

    create table stat_inventory_daily (
        in_amount numeric(16,2) not null,
        in_qty numeric(14,3) not null,
        out_qty numeric(14,3) not null,
        id integer,
        stat_date varchar(10) not null,
        warehouse_id varchar(20) not null,
        material_code varchar(30) not null,
        primary key (id)
    );

    create table stat_material_usage (
        out_qty numeric(14,3) not null,
        usage_days integer not null,
        period varchar(7) not null,
        id integer,
        warehouse_id varchar(20),
        material_code varchar(30) not null,
        primary key (id)
    );

    create table stat_order_monthly (
        order_count integer not null,
        total_amount numeric(16,2) not null,
        period varchar(7) not null,
        id integer,
        order_type varchar(20) not null,
        primary key (id)
    );

    create table stock_check (
        actual_qty numeric(14,3),
        adjust_qty numeric(14,3),
        diff_qty numeric(14,3),
        system_qty numeric(14,3),
        create_time timestamp,
        id integer,
        doc_type varchar(20) not null,
        from_location_id varchar(20),
        status varchar(20) not null,
        to_location_id varchar(20),
        warehouse_id varchar(20) not null,
        batch_no varchar(30),
        doc_no varchar(30) not null unique,
        material_code varchar(30) not null,
        from_location_name varchar(50),
        operator varchar(50),
        to_location_name varchar(50),
        material_name varchar(100),
        remark varchar(500),
        primary key (id)
    );

    create table supplier (
        blacklisted boolean,
        enabled boolean,
        processing_fee numeric(38,2),
        create_time timestamp,
        id integer,
        update_time timestamp,
        code varchar(20) not null unique,
        payment_method varchar(20),
        type varchar(20),
        name varchar(100) not null,
        payment_terms varchar(500),
        primary key (id)
    );

    create table supplier_quality_trace (
        arrival_date date,
        compensation_amount numeric(14,2),
        issue_date date,
        loss_amount numeric(38,2),
        print_count integer not null,
        purchase_amount numeric(38,2),
        purchase_qty numeric(38,2),
        purchase_unit_price numeric(38,2),
        resolve_date date,
        create_time timestamp,
        id integer,
        supplier_id bigint,
        update_time timestamp,
        category varchar(20),
        order_category varchar(20),
        qc_inspection_no varchar(20),
        qc_status varchar(20),
        status varchar(20) not null,
        trace_no varchar(20) not null unique,
        batch_no varchar(30) not null,
        material_code varchar(30),
        purchase_order_no varchar(30),
        result_type varchar(30),
        created_by varchar(50),
        handler varchar(50),
        material_name varchar(100),
        supplier_name varchar(100) not null,
        remark varchar(500),
        result_remark varchar(1000),
        description varchar(2000) not null,
        primary key (id)
    );

    create table sys_role (
        enabled boolean,
        create_time timestamp,
        id integer,
        update_time timestamp,
        code varchar(30) not null unique,
        name varchar(50) not null,
        primary key (id)
    );

    create table sys_role_permission (
        id integer,
        perm_code varchar(30) not null,
        role_code varchar(30) not null,
        primary key (id)
    );

    create table sys_user (
        enabled boolean,
        must_change_pwd boolean,
        create_time timestamp,
        id integer,
        update_time timestamp,
        phone varchar(20),
        role varchar(30) not null,
        real_name varchar(50) not null,
        username varchar(50) not null unique,
        password varchar(100) not null,
        primary key (id)
    );

    create table task (
        due_date date,
        completed_at timestamp,
        create_time timestamp,
        id integer,
        update_time timestamp,
        priority varchar(10) not null,
        status varchar(20) not null,
        doc_no varchar(25) not null unique,
        created_by varchar(50),
        owner varchar(50) not null,
        title varchar(200) not null,
        collaborators varchar(500),
        remark varchar(500),
        completed_note varchar(1000),
        description varchar(2000),
        primary key (id)
    );

    create table task_progress (
        create_time timestamp,
        id integer,
        task_id bigint not null,
        action_type varchar(20) not null,
        reporter varchar(50) not null,
        content varchar(1000),
        primary key (id)
    );

    create table voucher (
        attachment_count integer,
        total_credit numeric(14,2) not null,
        total_debit numeric(14,2) not null,
        voucher_date date not null,
        create_time timestamp,
        id integer,
        posted_time timestamp,
        update_time timestamp,
        period varchar(10) not null,
        doc_no varchar(20) not null unique,
        source varchar(20) not null,
        status varchar(20) not null,
        ref_doc_no varchar(30),
        created_by varchar(50),
        posted_by varchar(50),
        remark varchar(500),
        primary key (id)
    );

    create table voucher_entry (
        credit numeric(14,2),
        debit numeric(14,2),
        line_no integer,
        id integer,
        voucher_id bigint not null,
        aux_type varchar(20),
        subject_code varchar(20) not null,
        aux_name varchar(100),
        subject_name varchar(100) not null,
        digest varchar(200),
        primary key (id)
    );

    create table warehouse (
        enabled boolean,
        create_time timestamp,
        id integer,
        update_time timestamp,
        code varchar(20) not null unique,
        contact_phone varchar(20),
        processor_id varchar(20),
        warehouse_type varchar(20) not null,
        contact_person varchar(50),
        name varchar(50) not null,
        processor_name varchar(100),
        address varchar(200),
        remark varchar(500),
        primary key (id)
    );

    create table warehouse_location (
        enabled boolean,
        sort_order integer,
        create_time timestamp,
        id integer,
        update_time timestamp,
        zone_id bigint not null,
        code varchar(20) not null,
        name varchar(50) not null,
        remark varchar(500),
        primary key (id)
    );

    create table warehouse_zone (
        enabled boolean,
        sort_order integer,
        create_time timestamp,
        id integer,
        update_time timestamp,
        warehouse_id bigint not null,
        code varchar(20) not null,
        zone_type varchar(20),
        name varchar(50) not null,
        remark varchar(500),
        primary key (id)
    );

    create table weekly_topic (
        closed_date date,
        plan_date date,
        create_time timestamp,
        id integer,
        update_time timestamp,
        category varchar(20) not null,
        created_by varchar(50),
        owner varchar(50) not null,
        content varchar(2000) not null,
        result varchar(2000),
        primary key (id)
    );
