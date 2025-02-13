import React, { Fragment, useCallback, useEffect, useMemo } from 'react';
import { ServiceTypeSelector } from './ServiceTypeSelector';
import { ServiceForm } from './ServiceForm';

import { useCreateServiceMutation } from '../../store/ServiceApi';
import { useNavigate } from 'react-router-dom';
import { useSnackbar } from 'notistack';

export function ServiceCreator () {
    const { enqueueSnackbar } = useSnackbar();
    const [createService, createResult] = useCreateServiceMutation();
    const navigate = useNavigate();

    const service = useMemo(() => ({}), []);

    const create = useCallback((s) => {
        createService(s);
    }, [createService]);

    useEffect(() => {
        const { isSuccess } = createResult;
        if (isSuccess) {
            enqueueSnackbar('Service Created', { variant: 'success' });
            navigate('/services')
        }
    }, [createResult]);

    return (
        <Fragment>
            <ServiceTypeSelector service={ service }>
                {schema => <ServiceForm uiSchema={ schema } service={ service } onSave={ create } />}
            </ServiceTypeSelector>
        </Fragment>
    );
}