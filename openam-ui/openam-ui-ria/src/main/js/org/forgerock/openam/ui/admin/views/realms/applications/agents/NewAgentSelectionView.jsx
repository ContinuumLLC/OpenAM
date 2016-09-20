/**
* The contents of this file are subject to the terms of the Common Development and
* Distribution License (the License). You may not use this file except in compliance with the
* License.
*
* You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
* specific language governing permission and limitations under the License.
*
* When distributing Covered Software, include this CDDL Header Notice in each file and include
* the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
* Header, with the fields enclosed by brackets [] replaced by your own identifying
* information: "Portions copyright [year] [name of copyright owner]".
*
* Copyright 2016 ForgeRock AS.
*/

import { PageHeader, Panel, Clearfix, Col } from "react-bootstrap";
import { t } from "i18next";
import React, { Component } from "react";

import PageDescription from "components/PageDescription";
import Card from "components/Card";
import withRouter from "org/forgerock/commons/ui/common/components/hoc/withRouter";
import { getCreatableTypes } from "org/forgerock/openam/ui/admin/services/realm/AgentsService";

class NewAgentSelectionView extends Component {
    constructor (props) {
        super(props);
        this.state = {
            types: []
        };
    }
    componentDidMount () {
        getCreatableTypes(this.props.router.params[0]).then((response) => {
            this.setState({ types: response });
        });
    }
    render () {
        const creatableTypes = this.state.types.map((type) => (
        <Col sm={6} md={4}>
            <Card icon={"fa-male"} href={`#realms/${
                    encodeURIComponent(this.props.router.params[0])}/applications-agents/edit/${
                    encodeURIComponent(type._id)}`}>
                <h3 className="card-name card-name-sm am-text-lines-three text-primary hidden-md">{type.name}</h3>
                <h3 className="card-name card-name-sm am-text-lines-four text-primary visible-md-block">{type.name}</h3>
            </Card>
        </Col>
        ));
        const footer = (
            <Clearfix>
                <div className="pull-right">
                    <a href={`#realms/${encodeURIComponent(this.props.router.params[0])}/applications-agents"`}
                        className="btn fr-btn-secondary">{t ("common.form.cancel")}</a>
                </div>
            </Clearfix>
        );

        return (
            <div>
                <PageHeader bsClass="page-header page-header-no-border">
                    { t("console.applications.agents.newSelection.title") }
                </PageHeader>
                <PageDescription>
                    { t("console.applications.agents.newSelection.description") }
                </PageDescription>
                <Panel className="panel panel-default" footer={footer}>
                    <div className="grid-list">
                        { creatableTypes }
                    </div>
                </Panel>
            </div>
        );
    }
}

export default withRouter(NewAgentSelectionView);
